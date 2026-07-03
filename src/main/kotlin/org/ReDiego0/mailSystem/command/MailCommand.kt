package org.ReDiego0.mailSystem.command

import org.ReDiego0.mailSystem.api.MailBuilder
import org.ReDiego0.mailSystem.condition.ConditionEvaluator
import org.ReDiego0.mailSystem.config.ComplexMailConfig
import org.ReDiego0.mailSystem.config.ComplexMailLoader
import org.ReDiego0.mailSystem.config.MessageManager
import org.ReDiego0.mailSystem.config.SimpleMailTemplate
import org.ReDiego0.mailSystem.gui.MailGui
import org.ReDiego0.mailSystem.manager.MailManager
import org.ReDiego0.mailSystem.model.MailSource
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Executor

class MailCommand(
    private val plugin: JavaPlugin,
    private val manager: MailManager,
    private val gui: MailGui,
    private val template: SimpleMailTemplate,
    private val complexMailLoader: ComplexMailLoader,
    private val conditionEvaluator: ConditionEvaluator,
    private val msg: MessageManager
) : CommandExecutor {

    private val mainExecutor = Executor { Bukkit.getScheduler().runTask(plugin, it) }

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
        val message = args.drop(2).joinToString(" ")
        val parts = message.split("|")
        val title = parts[0]
        val body = parts.drop(1)

        if (title.isBlank()) {
            msg.sendMessage(sender, "errors.title_empty")
            return
        }

        when (target) {
            "*" -> sendSimpleToAll(sender, title, body)
            "@online" -> sendSimpleToOnline(sender, title, body)
            else -> sendSimpleToPlayer(sender, target, title, body)
        }
    }

    private fun sendSimpleToAll(sender: CommandSender, title: String, body: List<String>) {
        manager.getAllProfileUUIDs().thenAcceptAsync({ uuids ->
            for (uuid in uuids) {
                val mail = MailBuilder()
                    .sender(template.senderName, template.createSenderIcon())
                    .title(title)
                    .body(body)
                    .ttl(template.ttlDays)
                    .source(MailSource.Config)
                    .build(uuid)
                manager.deliverMail(mail)
            }
            msg.sendMessage(sender, "notifications.mail_sent", "count" to uuids.size.toString())
        }, mainExecutor)
    }

    private fun sendSimpleToOnline(sender: CommandSender, title: String, body: List<String>) {
        val onlinePlayers = Bukkit.getOnlinePlayers()
        for (player in onlinePlayers) {
            val mail = MailBuilder()
                .sender(template.senderName, template.createSenderIcon())
                .title(title)
                .body(body)
                .ttl(template.ttlDays)
                .source(MailSource.Config)
                .build(player.uniqueId)
            manager.deliverMail(mail)
        }
        msg.sendMessage(sender, "notifications.mail_sent", "count" to onlinePlayers.size.toString())
    }

    private fun sendSimpleToPlayer(sender: CommandSender, playerName: String, title: String, body: List<String>) {
        val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline) {
            msg.sendMessage(sender, "errors.player_not_found", "player" to playerName)
            return
        }
        val mail = MailBuilder()
            .sender(template.senderName, template.createSenderIcon())
            .title(title)
            .body(body)
            .ttl(template.ttlDays)
            .source(MailSource.Config)
            .build(offlinePlayer.uniqueId)
        manager.deliverMail(mail)
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
}
