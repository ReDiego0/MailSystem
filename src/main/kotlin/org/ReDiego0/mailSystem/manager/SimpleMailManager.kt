package org.ReDiego0.mailSystem.manager

import org.ReDiego0.mailSystem.api.MailApi
import org.ReDiego0.mailSystem.api.SimpleMailApi
import org.ReDiego0.mailSystem.model.Mail
import org.ReDiego0.mailSystem.model.MailProfile
import org.ReDiego0.mailSystem.model.MailStatus
import org.ReDiego0.mailSystem.reward.CommandReward
import org.ReDiego0.mailSystem.reward.PhysicalReward
import org.ReDiego0.mailSystem.storage.MailStorage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

class SimpleMailManager(
    private val storage: MailStorage,
    private val executor: ExecutorService,
    private val plugin: JavaPlugin,
    private val inboxCapacity: Int = 50
) : MailManager {

    private val api = SimpleMailApi(this)

    // ── Public API ───────────────────────────────────────────────────────

    override fun deliverMail(mail: Mail): CompletableFuture<Void> {
        val future = CompletableFuture.supplyAsync({
            val currentCount = storage.getMailCount(mail.recipientUUID).join()
            val shouldBeQueued = currentCount >= inboxCapacity
            val mailToSave = mail.copy(queued = shouldBeQueued)
            storage.saveMail(mailToSave).join()
            shouldBeQueued
        }, executor)

        future.thenAcceptAsync({ wasQueued ->
            val recipient = Bukkit.getPlayer(mail.recipientUUID)
            if (recipient != null && recipient.isOnline && !wasQueued) {
                notifyNewMail(recipient)
            }
        }, executor)

        return future.thenApply { null }
    }

    override fun claimRewards(mailId: UUID, player: Player): CompletableFuture<ClaimResult> =
        CompletableFuture.supplyAsync({
            val mail = storage.getMail(mailId).join()
                ?: return@supplyAsync ClaimResult.MailNotFound

            if (mail.status == MailStatus.CLAIMED)
                return@supplyAsync ClaimResult.AlreadyClaimed

            if (System.currentTimeMillis() > mail.expiresAt)
                return@supplyAsync ClaimResult.Expired

            val rewards = storage.getRewards(mailId).join()
            if (rewards.isEmpty()) {
                storage.updateMailStatus(mailId, MailStatus.CLAIMED).join()
                return@supplyAsync ClaimResult.NoRewards
            }

            val physicalRewards = rewards.filterIsInstance<PhysicalReward>()
            if (!hasInventorySpace(player, physicalRewards))
                return@supplyAsync ClaimResult.InventoryFull

            val commandRewards = rewards.filterIsInstance<CommandReward>()
            for (reward in commandRewards) {
                for (command in reward.commands) {
                    val parsed = command.replace("%player%", player.name)
                    val success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed)
                    if (!success) return@supplyAsync ClaimResult.CommandFailed(parsed, "Dispatch returned false")
                }
            }

            if (physicalRewards.isNotEmpty()) {
                val syncFuture = CompletableFuture<Void>()
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    for (reward in physicalRewards) {
                        player.inventory.addItem(reward.item)
                    }
                    syncFuture.complete(null)
                })
                syncFuture.join()
            }

            storage.updateMailStatus(mailId, MailStatus.CLAIMED).join()
            ClaimResult.Success
        }, executor)

    override fun deleteMail(mailId: UUID, playerUUID: UUID): CompletableFuture<Boolean> =
        CompletableFuture.supplyAsync({
            val mail = storage.getMail(mailId).join() ?: return@supplyAsync false
            if (mail.recipientUUID != playerUUID) return@supplyAsync false
            val deleted = storage.deleteMail(mailId).join()
            if (deleted) {
                promoteQueuedIfNeeded(playerUUID)
            }
            deleted
        }, executor)

    override fun deleteAllRead(playerUUID: UUID): CompletableFuture<Int> =
        CompletableFuture.supplyAsync({
            val count = storage.deleteAllRead(playerUUID).join()
            if (count > 0) {
                promoteQueuedIfNeeded(playerUUID)
            }
            count
        }, executor)

    override fun expireOldMails(): CompletableFuture<Int> =
        CompletableFuture.supplyAsync({
            val affectedPlayers = storage.getExpiredPlayerUUIDs().join()
            val count = storage.expireOldMails().join()
            for (uuid in affectedPlayers) {
                promoteQueuedIfNeeded(uuid)
            }
            count
        }, executor)

    override fun loadProfile(playerUUID: UUID): CompletableFuture<MailProfile> =
        CompletableFuture.supplyAsync({
            var profile = storage.loadProfile(playerUUID).join()
            if (profile == null) {
                profile = MailProfile(
                    playerUUID = playerUUID,
                    playerName = Bukkit.getOfflinePlayer(playerUUID).name ?: "Unknown",
                    mails = mutableListOf(),
                    lastAccessed = System.currentTimeMillis()
                )
                storage.saveProfile(profile).join()
            }
            profile
        }, executor)

    override fun saveProfile(profile: MailProfile): CompletableFuture<Void> =
        storage.saveProfile(profile)

    override fun getAllProfileUUIDs(): CompletableFuture<List<UUID>> =
        storage.getAllProfileUUIDs()

    override fun getApi(): MailApi = api

    fun getStorage(): MailStorage = storage

    // ── Internal ─────────────────────────────────────────────────────────

    private fun promoteQueuedIfNeeded(playerUUID: UUID) {
        val currentCount = storage.getMailCount(playerUUID).join()
        if (currentCount < inboxCapacity) {
            val promoted = storage.promoteOldestQueued(playerUUID).join()
            if (promoted) {
                val recipient = Bukkit.getPlayer(playerUUID)
                if (recipient != null && recipient.isOnline) {
                    notifyNewMail(recipient)
                }
            }
        }
    }

    private fun notifyNewMail(player: Player) {
        player.sendMessage("§eYou have new mail! Open your inbox with §6/mail")
    }

    private fun hasInventorySpace(player: Player, rewards: List<PhysicalReward>): Boolean {
        val inventory = player.inventory
        var emptySlots = inventory.storageContents.count { it == null || it.type == Material.AIR }

        for (reward in rewards) {
            val maxStack = reward.item.maxStackSize
            var remaining = reward.item.amount

            for (slot in inventory.storageContents) {
                if (slot != null && slot.isSimilar(reward.item) && slot.amount < maxStack) {
                    remaining -= (maxStack - slot.amount)
                    if (remaining <= 0) break
                }
            }

            while (remaining > 0 && emptySlots > 0) {
                remaining -= maxStack
                emptySlots--
            }

            if (remaining > 0) return false
        }
        return true
    }
}
