package org.ReDiego0.mailSystem.command

import org.ReDiego0.mailSystem.api.MailBuilder
import org.ReDiego0.mailSystem.condition.ConditionEvaluator
import org.ReDiego0.mailSystem.config.ComplexMailConfig
import org.ReDiego0.mailSystem.config.ComplexMailLoader
import org.ReDiego0.mailSystem.config.MessageManager
import org.ReDiego0.mailSystem.config.SimpleMailTemplate
import org.ReDiego0.mailSystem.gui.MailGui
import org.ReDiego0.mailSystem.logging.AuditLogger
import org.ReDiego0.mailSystem.manager.MailManager
import org.ReDiego0.mailSystem.manager.SimpleMailManager
import org.ReDiego0.mailSystem.model.MailSource
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Executor

class MailCommand(
    private val plugin: JavaPlugin,
    private val manager: MailManager,
    private val gui: MailGui,
    private val template: SimpleMailTemplate,
    private val complexMailLoader: ComplexMailLoader,
    private val conditionEvaluator: ConditionEvaluator,
    private val msg: MessageManager,
    private val auditLogger: AuditLogger
) : CommandExecutor {

    private val mainExecutor = Executor { Bukkit.getScheduler().runTask(plugin, it) }
    private val purgeConfirmations = HashMap<String, Long>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            if (sender !is Player) {
                msg.sendMessage(sender, "errors.only_players_gui")
                return true
            }
            gui.open(sender)
            return true
        }

        when (args[0].lowercase()) {
            "send" -> handleSend(sender, args)
            "sendcomplex" -> handleSendComplex(sender, args)
            "purge" -> handlePurge(sender, args)
            else -> msg.sendMessage(sender, "errors.unknown_subcommand", "subcommand" to args[0])
        }

        return true
    }

    // ── Simple Send ──────────────────────────────────────────────────────

    private fun handleSend(sender: CommandSender, args: Array<String>) {
        if (!sender.hasPermission("mailsystem.command.send")) {
            msg.sendMessage(sender, "errors.no_permission")
            return
        }

        if (args.size < 3) {
            msg.sendMessage(sender, "errors.usage_send")
            return
        }

        val target = args[1]
        val physicalRewards = mutableListOf<Pair<String, Int>>()
        val commandRewards = mutableListOf<String>()
        val messageParts = mutableListOf<String>()

        var i = 2
        while (i < args.size) {
            when (args[i]) {
                "--reward" -> {
                    if (i + 1 < args.size) {
                        val parts = args[i + 1].split(":")
                        val material = parts[0].uppercase()
                        val amount = if (parts.size > 1) parts[1].toIntOrNull() ?: 1 else 1
                        physicalRewards.add(material to amount)
                        i += 2
                    } else { i++ }
                }
                "--reward-cmd" -> {
                    if (i + 1 < args.size) {
                        commandRewards.add(args[i + 1])
                        i += 2
                    } else { i++ }
                }
                else -> {
                    messageParts.add(args[i])
                    i++
                }
            }
        }

        val message = messageParts.joinToString(" ")
        val parts = message.split("|")
        val title = parts[0]
        val body = parts.drop(1)

        if (title.isBlank()) {
            msg.sendMessage(sender, "errors.title_empty")
            return
        }

        when (target) {
            "*" -> sendSimpleToAll(sender, title, body, physicalRewards, commandRewards)
            "@online" -> sendSimpleToOnline(sender, title, body, physicalRewards, commandRewards)
            else -> sendSimpleToPlayer(sender, target, title, body, physicalRewards, commandRewards)
        }
    }

    private fun buildRewards(
        physical: List<Pair<String, Int>>,
        commands: List<String>,
        builder: MailBuilder
    ): MailBuilder {
        for ((materialName, amount) in physical) {
            val material = try { Material.valueOf(materialName) } catch (_: IllegalArgumentException) { continue }
            builder.addPhysicalReward(ItemStack(material, amount))
        }
        for (command in commands) {
            val displayItem = ItemStack(Material.PAPER)
            val meta = displayItem.itemMeta
            meta?.displayName(net.kyori.adventure.text.Component.text("Command Reward", net.kyori.adventure.text.format.NamedTextColor.GOLD))
            meta?.lore(listOf(net.kyori.adventure.text.Component.text(command, net.kyori.adventure.text.format.NamedTextColor.GRAY)))
            displayItem.itemMeta = meta
            builder.addCommandReward(displayItem, listOf(command))
        }
        return builder
    }

    private fun sendSimpleToAll(
        sender: CommandSender, title: String, body: List<String>,
        physical: List<Pair<String, Int>>, commands: List<String>
    ) {
        manager.getAllProfileUUIDs().thenAcceptAsync({ uuids ->
            for (uuid in uuids) {
                val builder = MailBuilder()
                    .sender(template.senderName, template.createSenderIcon())
                    .title(title).body(body).ttl(template.ttlDays)
                    .source(MailSource.Config)
                buildRewards(physical, commands, builder)
                manager.deliverMail(builder.build(uuid))
            }
            msg.sendMessage(sender, "notifications.mail_sent", "count" to uuids.size.toString())
        }, mainExecutor)
    }

    private fun sendSimpleToOnline(
        sender: CommandSender, title: String, body: List<String>,
        physical: List<Pair<String, Int>>, commands: List<String>
    ) {
        val onlinePlayers = Bukkit.getOnlinePlayers()
        for (player in onlinePlayers) {
            val builder = MailBuilder()
                .sender(template.senderName, template.createSenderIcon())
                .title(title).body(body).ttl(template.ttlDays)
                .source(MailSource.Config)
            buildRewards(physical, commands, builder)
            manager.deliverMail(builder.build(player.uniqueId))
        }
        msg.sendMessage(sender, "notifications.mail_sent", "count" to onlinePlayers.size.toString())
    }

    private fun sendSimpleToPlayer(
        sender: CommandSender, playerName: String, title: String, body: List<String>,
        physical: List<Pair<String, Int>>, commands: List<String>
    ) {
        val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline) {
            msg.sendMessage(sender, "errors.player_not_found", "player" to playerName)
            return
        }
        val builder = MailBuilder()
            .sender(template.senderName, template.createSenderIcon())
            .title(title).body(body).ttl(template.ttlDays)
            .source(MailSource.Config)
        buildRewards(physical, commands, builder)
        manager.deliverMail(builder.build(offlinePlayer.uniqueId))
        msg.sendMessage(sender, "notifications.mail_sent_to", "player" to playerName)
    }

    // ── Complex Send ─────────────────────────────────────────────────────

    private fun handleSendComplex(sender: CommandSender, args: Array<String>) {
        if (!sender.hasPermission("mailsystem.command.sendcomplex")) {
            msg.sendMessage(sender, "errors.no_permission_complex")
            return
        }

        if (args.size < 3) {
            msg.sendMessage(sender, "errors.usage_sendcomplex")
            return
        }

        val target = args[1]
        val id = args[2]
        val config = complexMailLoader.getConfig(id)

        if (config == null) {
            msg.sendMessage(sender, "errors.complex_not_found", "id" to id)
            return
        }

        when (target) {
            "*" -> sendComplexToAll(sender, config)
            "@online" -> sendComplexToOnline(sender, config)
            else -> sendComplexToPlayer(sender, target, config)
        }
    }

    private fun sendComplexToAll(sender: CommandSender, config: ComplexMailConfig) {
        manager.getAllProfileUUIDs().thenAcceptAsync({ uuids ->
            val filteredByOnline = if (config.effectiveRequiresOnline) {
                uuids.filter { Bukkit.getPlayer(it)?.isOnline == true }
            } else {
                uuids
            }

            val targets = if (config.conditions != null) {
                filteredByOnline.filter { conditionEvaluator.evaluate(it, config.conditions) }
            } else {
                filteredByOnline
            }

            for (uuid in targets) {
                manager.deliverMail(config.buildMail(uuid))
            }
            msg.sendMessage(sender, "notifications.complex_sent", "id" to config.id, "count" to targets.size.toString())
        }, mainExecutor)
    }

    private fun sendComplexToOnline(sender: CommandSender, config: ComplexMailConfig) {
        val onlinePlayers = Bukkit.getOnlinePlayers()
        val targets = if (config.conditions != null) {
            onlinePlayers.filter { conditionEvaluator.evaluate(it.uniqueId, config.conditions) }
        } else {
            onlinePlayers.toList()
        }

        for (player in targets) {
            manager.deliverMail(config.buildMail(player.uniqueId))
        }
        msg.sendMessage(sender, "notifications.complex_sent", "id" to config.id, "count" to targets.size.toString())
    }

    private fun sendComplexToPlayer(sender: CommandSender, playerName: String, config: ComplexMailConfig) {
        val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline) {
            msg.sendMessage(sender, "errors.player_not_found", "player" to playerName)
            return
        }
        if (config.effectiveRequiresOnline && !offlinePlayer.isOnline) {
            msg.sendMessage(sender, "errors.player_must_be_online", "player" to playerName)
            return
        }
        if (config.conditions != null && !conditionEvaluator.evaluate(offlinePlayer.uniqueId, config.conditions)) {
            msg.sendMessage(sender, "errors.conditions_not_met", "player" to playerName)
            return
        }
        manager.deliverMail(config.buildMail(offlinePlayer.uniqueId))
        msg.sendMessage(sender, "notifications.complex_sent_to", "id" to config.id, "player" to playerName)
    }

    // ── Purge ────────────────────────────────────────────────────────────

    private fun handlePurge(sender: CommandSender, args: Array<String>) {
        if (!sender.hasPermission("mailsystem.command.purge")) {
            msg.sendMessage(sender, "errors.no_permission_purge")
            return
        }

        if (args.size < 2) {
            msg.sendMessage(sender, "errors.usage_purge")
            return
        }

        val playerName = args[1]
        val offlinePlayer = Bukkit.getOfflinePlayer(playerName)

        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline) {
            msg.sendMessage(sender, "errors.player_not_found", "player" to playerName)
            return
        }

        val lastClick = purgeConfirmations[sender.name] ?: 0
        val now = System.currentTimeMillis()

        if (now - lastClick > 3000) {
            purgeConfirmations[sender.name] = now
            msg.sendMessage(sender, "notifications.purge_confirm", "player" to playerName)
        } else {
            purgeConfirmations.remove(sender.name)
            val storage = (manager as SimpleMailManager).getStorage()
            storage.deleteAllMails(offlinePlayer.uniqueId).thenAcceptAsync({ count ->
                if (count > 0) {
                    auditLogger.logPurge(sender.name, playerName, count)
                    msg.sendMessage(sender, "notifications.purge_success", "player" to playerName, "count" to count.toString())
                } else {
                    msg.sendMessage(sender, "errors.no_mails_to_purge", "player" to playerName)
                }
            }, mainExecutor)
        }
    }
}
