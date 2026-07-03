package org.ReDiego0.mailSystem.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.ReDiego0.mailSystem.model.Mail
import org.ReDiego0.mailSystem.model.MailProfile
import org.ReDiego0.mailSystem.model.MailSource
import org.ReDiego0.mailSystem.model.MailStatus
import org.ReDiego0.mailSystem.reward.CommandReward
import org.ReDiego0.mailSystem.reward.PhysicalReward
import org.ReDiego0.mailSystem.reward.Reward
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

class SqlMailStorage(
    private val config: StorageConfig,
    private val executor: ExecutorService,
    private val plugin: JavaPlugin
) : MailStorage {

    private lateinit var dataSource: HikariDataSource

    override fun initialize(): CompletableFuture<Void> = CompletableFuture.runAsync({
        dataSource = createDataSource()
        createTables()
        plugin.logger.info("Storage initialized: ${config.type}")
    }, executor)

    override fun shutdown(): CompletableFuture<Void> = CompletableFuture.runAsync({
        if (::dataSource.isInitialized) dataSource.close()
    }, executor)

    // ── Profile ──────────────────────────────────────────────────────────

    override fun loadProfile(playerUUID: UUID): CompletableFuture<MailProfile?> =
        CompletableFuture.supplyAsync({
            withConnection { conn ->
                val profile = conn.loadProfileRow(playerUUID) ?: return@withConnection null
                val mails = conn.loadMailsFor(playerUUID)
                profile.copy(mails = mails.toMutableList())
            }
        }, executor)

    override fun saveProfile(profile: MailProfile): CompletableFuture<Void> =
        CompletableFuture.runAsync({
            withConnection { conn ->
                conn.prepareStatement(
                    """INSERT OR REPLACE INTO profiles (player_uuid, player_name, max_capacity, last_accessed)
                       VALUES (?, ?, ?, ?)"""
                ).use { stmt ->
                    stmt.setString(1, profile.playerUUID.toString())
                    stmt.setString(2, profile.playerName)
                    stmt.setInt(3, profile.maxCapacity)
                    stmt.setLong(4, profile.lastAccessed)
                    stmt.executeUpdate()
                }
            }
        }, executor)

    // ── Mail CRUD ────────────────────────────────────────────────────────

    override fun saveMail(mail: Mail): CompletableFuture<Void> =
        CompletableFuture.runAsync({
            withConnection { conn ->
                conn.autoCommit = false
                try {
                    conn.insertMail(mail)
                    conn.insertRewards(mail.id, mail.rewards)
                    conn.commit()
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        }, executor)

    override fun getMail(mailId: UUID): CompletableFuture<Mail?> =
        CompletableFuture.supplyAsync({
            withConnection { conn -> conn.loadMailRow(mailId) }
        }, executor)

    override fun getMails(playerUUID: UUID): CompletableFuture<List<Mail>> =
        CompletableFuture.supplyAsync({
            withConnection { conn -> conn.loadMailsFor(playerUUID) }
        }, executor)

    override fun updateMailStatus(mailId: UUID, status: MailStatus): CompletableFuture<Boolean> =
        CompletableFuture.supplyAsync({
            withConnection { conn ->
                conn.prepareStatement("UPDATE mails SET status = ? WHERE id = ?").use { stmt ->
                    stmt.setString(1, status.name)
                    stmt.setString(2, mailId.toString())
                    stmt.executeUpdate() > 0
                }
            }
        }, executor)

    override fun deleteMail(mailId: UUID): CompletableFuture<Boolean> =
        CompletableFuture.supplyAsync({
            withConnection { conn ->
                conn.prepareStatement("DELETE FROM mails WHERE id = ?").use { stmt ->
                    stmt.setString(1, mailId.toString())
                    stmt.executeUpdate() > 0
                }
            }
        }, executor)

    override fun deleteAllRead(playerUUID: UUID): CompletableFuture<Int> =
        CompletableFuture.supplyAsync({
            withConnection { conn ->
                conn.prepareStatement(
                    "DELETE FROM mails WHERE recipient_uuid = ? AND status = ?"
                ).use { stmt ->
                    stmt.setString(1, playerUUID.toString())
                    stmt.setString(2, MailStatus.READ.name)
                    stmt.executeUpdate()
                }
            }
        }, executor)

    override fun expireOldMails(): CompletableFuture<Int> =
        CompletableFuture.supplyAsync({
            withConnection { conn ->
                conn.prepareStatement("DELETE FROM mails WHERE expires_at < ?").use { stmt ->
                    stmt.setLong(1, System.currentTimeMillis())
                    stmt.executeUpdate()
                }
            }
        }, executor)

    override fun hasUnreadMail(playerUUID: UUID): CompletableFuture<Boolean> =
        CompletableFuture.supplyAsync({
            withConnection { conn ->
                conn.prepareStatement(
                    "SELECT 1 FROM mails WHERE recipient_uuid = ? AND status = ? LIMIT 1"
                ).use { stmt ->
                    stmt.setString(1, playerUUID.toString())
                    stmt.setString(2, MailStatus.UNREAD.name)
                    stmt.executeQuery().next()
                }
            }
        }, executor)

    // ── Rewards ──────────────────────────────────────────────────────────

    override fun saveRewards(mailId: UUID, rewards: List<Reward>): CompletableFuture<Void> =
        CompletableFuture.runAsync({
            withConnection { conn -> conn.insertRewards(mailId, rewards) }
        }, executor)

    override fun getRewards(mailId: UUID): CompletableFuture<List<Reward>> =
        CompletableFuture.supplyAsync({
            withConnection { conn -> conn.loadRewardsFor(mailId) }
        }, executor)

    override fun markRewardClaimed(rewardId: Int): CompletableFuture<Boolean> =
        CompletableFuture.supplyAsync({
            withConnection { conn ->
                conn.prepareStatement("UPDATE rewards SET claimed = 1 WHERE id = ?").use { stmt ->
                    stmt.setInt(1, rewardId)
                    stmt.executeUpdate() > 0
                }
            }
        }, executor)

    override fun getAllProfileUUIDs(): CompletableFuture<List<UUID>> =
        CompletableFuture.supplyAsync({
            withConnection { conn ->
                conn.prepareStatement("SELECT player_uuid FROM profiles").use { stmt ->
                    val rs = stmt.executeQuery()
                    val uuids = mutableListOf<UUID>()
                    while (rs.next()) {
                        uuids.add(UUID.fromString(rs.getString("player_uuid")))
                    }
                    uuids
                }
            }
        }, executor)

    // ── Queue ────────────────────────────────────────────────────────────

    override fun getMailCount(playerUUID: UUID): CompletableFuture<Int> =
        CompletableFuture.supplyAsync({
            withConnection { conn ->
                conn.prepareStatement("SELECT COUNT(*) FROM mails WHERE recipient_uuid = ? AND queued = 0").use { stmt ->
                    stmt.setString(1, playerUUID.toString())
                    val rs = stmt.executeQuery()
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
        }, executor)

    override fun getQueuedMails(playerUUID: UUID): CompletableFuture<List<Mail>> =
        CompletableFuture.supplyAsync({
            withConnection { conn ->
                conn.prepareStatement("SELECT * FROM mails WHERE recipient_uuid = ? AND queued = 1 ORDER BY created_at ASC").use { stmt ->
                    stmt.setString(1, playerUUID.toString())
                    val rs = stmt.executeQuery()
                    val mails = mutableListOf<Mail>()
                    while (rs.next()) {
                        val mailId = UUID.fromString(rs.getString("id"))
                        mails.add(Mail(
                            id = mailId,
                            recipientUUID = playerUUID,
                            senderName = rs.getString("sender_name"),
                            senderIcon = ItemSerializer.deserialize(rs.getString("sender_icon")),
                            title = rs.getString("title"),
                            body = deserializeStringList(rs.getString("body")),
                            rewards = conn.loadRewardsFor(mailId),
                            createdAt = rs.getLong("created_at"),
                            expiresAt = rs.getLong("expires_at"),
                            status = MailStatus.valueOf(rs.getString("status")),
                            source = MailSource.fromStorageString(rs.getString("source")),
                            queued = true
                        ))
                    }
                    mails
                }
            }
        }, executor)

    override fun promoteOldestQueued(playerUUID: UUID): CompletableFuture<Boolean> =
        CompletableFuture.supplyAsync({
            withConnection { conn ->
                conn.prepareStatement(
                    "UPDATE mails SET queued = 0 WHERE id = (SELECT id FROM mails WHERE recipient_uuid = ? AND queued = 1 ORDER BY created_at ASC LIMIT 1)"
                ).use { stmt ->
                    stmt.setString(1, playerUUID.toString())
                    stmt.executeUpdate() > 0
                }
            }
        }, executor)

    override fun getExpiredPlayerUUIDs(): CompletableFuture<List<UUID>> =
        CompletableFuture.supplyAsync({
            withConnection { conn ->
                conn.prepareStatement("SELECT DISTINCT recipient_uuid FROM mails WHERE expires_at < ?").use { stmt ->
                    stmt.setLong(1, System.currentTimeMillis())
                    val rs = stmt.executeQuery()
                    val uuids = mutableListOf<UUID>()
                    while (rs.next()) {
                        uuids.add(UUID.fromString(rs.getString("recipient_uuid")))
                    }
                    uuids
                }
            }
        }, executor)

    // ── Internal: DataSource & Schema ────────────────────────────────────

    private fun createDataSource(): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            when {
                config.isSqlite -> {
                    val dbFile = File(plugin.dataFolder, config.sqliteFile)
                    plugin.dataFolder.mkdirs()
                    jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
                    connectionTestQuery = "SELECT 1"
                }
                config.isMysql -> {
                    jdbcUrl = "jdbc:mysql://${config.mysqlHost}:${config.mysqlPort}/${config.mysqlDatabase}"
                    username = config.mysqlUsername
                    password = config.mysqlPassword
                    maximumPoolSize = config.mysqlPoolSize
                }
                else -> throw IllegalArgumentException("Unknown storage type: ${config.type}")
            }
        }
        return HikariDataSource(hikariConfig)
    }

    private fun createTables() {
        withConnection { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """CREATE TABLE IF NOT EXISTS profiles (
                        player_uuid   VARCHAR(36) PRIMARY KEY,
                        player_name   VARCHAR(16) NOT NULL,
                        max_capacity  INT NOT NULL DEFAULT 50,
                        last_accessed BIGINT NOT NULL
                    )"""
                )
                stmt.execute(
                    """CREATE TABLE IF NOT EXISTS mails (
                        id             VARCHAR(36) PRIMARY KEY,
                        recipient_uuid VARCHAR(36) NOT NULL,
                        sender_name    VARCHAR(255) NOT NULL,
                        sender_icon    TEXT NOT NULL,
                        title          VARCHAR(255) NOT NULL,
                        body           TEXT NOT NULL,
                        status         VARCHAR(10) NOT NULL DEFAULT 'UNREAD',
                        source         VARCHAR(50) NOT NULL,
                        created_at     BIGINT NOT NULL,
                        expires_at     BIGINT NOT NULL,
                        queued         TINYINT NOT NULL DEFAULT 0
                    )"""
                )
                stmt.execute(
                    """CREATE TABLE IF NOT EXISTS rewards (
                        id            INTEGER PRIMARY KEY ${autoIncrementKeyword()},
                        mail_id       VARCHAR(36) NOT NULL,
                        type          VARCHAR(10) NOT NULL,
                        display_item  TEXT NOT NULL,
                        item_data     TEXT,
                        commands      TEXT,
                        claimed       TINYINT NOT NULL DEFAULT 0,
                        FOREIGN KEY (mail_id) REFERENCES mails(id) ON DELETE CASCADE
                    )"""
                )
            }
        }
    }

    private fun autoIncrementKeyword(): String =
        if (config.isSqlite) "AUTOINCREMENT" else "AUTO_INCREMENT"

    // ── Internal: Connection helper ──────────────────────────────────────

    private fun <T> withConnection(block: (Connection) -> T): T =
        dataSource.connection.use { block(it) }

    // ── Internal: Insert helpers ─────────────────────────────────────────

    private fun Connection.insertMail(mail: Mail) {
        prepareStatement(
            """INSERT INTO mails (id, recipient_uuid, sender_name, sender_icon, title, body, status, source, created_at, expires_at, queued)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        ).use { stmt ->
            stmt.setString(1, mail.id.toString())
            stmt.setString(2, mail.recipientUUID.toString())
            stmt.setString(3, mail.senderName)
            stmt.setString(4, ItemSerializer.serialize(mail.senderIcon))
            stmt.setString(5, mail.title)
            stmt.setString(6, serializeStringList(mail.body))
            stmt.setString(7, mail.status.name)
            stmt.setString(8, mail.source.toStorageString())
            stmt.setLong(9, mail.createdAt)
            stmt.setLong(10, mail.expiresAt)
            stmt.setInt(11, if (mail.queued) 1 else 0)
            stmt.executeUpdate()
        }
    }

    private fun Connection.insertRewards(mailId: UUID, rewards: List<Reward>) {
        prepareStatement(
            """INSERT INTO rewards (mail_id, type, display_item, item_data, commands, claimed)
               VALUES (?, ?, ?, ?, ?, 0)"""
        ).use { stmt ->
            for (reward in rewards) {
                stmt.setString(1, mailId.toString())
                when (reward) {
                    is PhysicalReward -> {
                        stmt.setString(2, "physical")
                        stmt.setString(3, ItemSerializer.serialize(reward.displayItem))
                        stmt.setString(4, ItemSerializer.serialize(reward.item))
                        stmt.setNull(5, java.sql.Types.VARCHAR)
                    }
                    is CommandReward -> {
                        stmt.setString(2, "command")
                        stmt.setString(3, ItemSerializer.serialize(reward.displayItem))
                        stmt.setNull(4, java.sql.Types.VARCHAR)
                        stmt.setString(5, serializeStringList(reward.commands))
                    }
                }
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    // ── Internal: Load helpers ───────────────────────────────────────────

    private fun Connection.loadProfileRow(playerUUID: UUID): MailProfile? {
        prepareStatement("SELECT * FROM profiles WHERE player_uuid = ?").use { stmt ->
            stmt.setString(1, playerUUID.toString())
            val rs = stmt.executeQuery()
            return if (rs.next()) {
                MailProfile(
                    playerUUID = playerUUID,
                    playerName = rs.getString("player_name"),
                    mails = mutableListOf(),
                    maxCapacity = rs.getInt("max_capacity"),
                    lastAccessed = rs.getLong("last_accessed")
                )
            } else null
        }
    }

    private fun Connection.loadMailsFor(playerUUID: UUID): List<Mail> {
        prepareStatement(
            "SELECT * FROM mails WHERE recipient_uuid = ? AND queued = 0 ORDER BY created_at DESC"
        ).use { stmt ->
            stmt.setString(1, playerUUID.toString())
            val rs = stmt.executeQuery()
            val mails = mutableListOf<Mail>()
            while (rs.next()) {
                val mailId = UUID.fromString(rs.getString("id"))
                mails.add(
                    Mail(
                        id = mailId,
                        recipientUUID = playerUUID,
                        senderName = rs.getString("sender_name"),
                        senderIcon = ItemSerializer.deserialize(rs.getString("sender_icon")),
                        title = rs.getString("title"),
                        body = deserializeStringList(rs.getString("body")),
                        rewards = loadRewardsFor(mailId),
                        createdAt = rs.getLong("created_at"),
                        expiresAt = rs.getLong("expires_at"),
                        status = MailStatus.valueOf(rs.getString("status")),
                        source = MailSource.fromStorageString(rs.getString("source")),
                        queued = rs.getInt("queued") == 1
                    )
                )
            }
            return mails
        }
    }

    private fun Connection.loadMailRow(mailId: UUID): Mail? {
        prepareStatement("SELECT * FROM mails WHERE id = ?").use { stmt ->
            stmt.setString(1, mailId.toString())
            val rs = stmt.executeQuery()
            return if (rs.next()) {
                val recipientUUID = UUID.fromString(rs.getString("recipient_uuid"))
                Mail(
                    id = mailId,
                    recipientUUID = recipientUUID,
                    senderName = rs.getString("sender_name"),
                    senderIcon = ItemSerializer.deserialize(rs.getString("sender_icon")),
                    title = rs.getString("title"),
                    body = deserializeStringList(rs.getString("body")),
                    rewards = loadRewardsFor(mailId),
                    createdAt = rs.getLong("created_at"),
                    expiresAt = rs.getLong("expires_at"),
                    status = MailStatus.valueOf(rs.getString("status")),
                    source = MailSource.fromStorageString(rs.getString("source")),
                    queued = rs.getInt("queued") == 1
                )
            } else null
        }
    }

    private fun Connection.loadRewardsFor(mailId: UUID): List<Reward> {
        prepareStatement("SELECT * FROM rewards WHERE mail_id = ?").use { stmt ->
            stmt.setString(1, mailId.toString())
            val rs = stmt.executeQuery()
            val rewards = mutableListOf<Reward>()
            while (rs.next()) {
                when (rs.getString("type")) {
                    "physical" -> rewards.add(
                        PhysicalReward(
                            item = ItemSerializer.deserialize(rs.getString("item_data"))
                        )
                    )
                    "command" -> rewards.add(
                        CommandReward(
                            displayItem = ItemSerializer.deserialize(rs.getString("display_item")),
                            commands = deserializeStringList(rs.getString("commands"))
                        )
                    )
                }
            }
            return rewards
        }
    }

    // ── Internal: List serialization ─────────────────────────────────────

    private fun serializeStringList(list: List<String>): String {
        val config = YamlConfiguration()
        config.set("l", list)
        return config.saveToString()
    }

    private fun deserializeStringList(data: String): List<String> {
        val config = YamlConfiguration()
        config.loadFromString(data)
        return config.getStringList("l")
    }
}
