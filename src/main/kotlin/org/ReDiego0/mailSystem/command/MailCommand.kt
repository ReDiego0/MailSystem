package org.ReDiego0.mailSystem.command

import org.ReDiego0.mailSystem.api.MailBuilder
import org.ReDiego0.mailSystem.config.ComplexMailLoader
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
import java.util.UUID
import java.util.concurrent.Executor

class MailCommand(
    private val plugin: JavaPlugin,
    private val manager: MailManager,
    private val gui: MailGui,
    private val template: SimpleMailTemplate,
    private val complexMailLoader: ComplexMailLoader
) : CommandExecutor {

    private val mainExecutor = Executor { Bukkit.getScheduler().runTask(plugin, it) }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            if (sender !is Player) {
                sender.sendMessage("\u00a7cOnly players can open the mail GUI.")
                return true
            }
            gui.open(sender)
            return true
        }

        when (args[0].lowercase()) {
            "send" -> handleSend(sender, args)
            "sendcomplex" -> handleSendComplex(sender, args)
            else -> sender.sendMessage("\u00a7cUnknown subcommand: ${args[0]}")
        }

        return true
    }

    // ── Simple Send ──────────────────────────────────────────────────────

    private fun handleSend(sender: CommandSender, args: Array<String>) {
        if (!sender.hasPermission("mailsystem.command.send")) {
            sender.sendMessage("\u00a7cYou don't have permission to send mail.")
            return
        }

        if (args.size < 3) {
            sender.sendMessage("\u00a7cUsage: /mail send <player|*|@online> <title|line2|line3>")
            return
        }

        val target = args[1]
        val message = args.drop(2).joinToString(" ")
        val parts = message.split("|")
        val title = parts[0]
        val body = parts.drop(1)

        if (title.isBlank()) {
            sender.sendMessage("\u00a7cTitle cannot be empty.")
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
            sender.sendMessage("\u00a7aSent mail to ${uuids.size} player(s).")
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
        sender.sendMessage("\u00a7aSent mail to ${onlinePlayers.size} player(s).")
    }

    private fun sendSimpleToPlayer(sender: CommandSender, playerName: String, title: String, body: List<String>) {
        val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline) {
            sender.sendMessage("\u00a7cPlayer '$playerName' has never played on this server.")
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
        sender.sendMessage("\u00a7aSent mail to $playerName.")
    }

    // ── Complex Send ─────────────────────────────────────────────────────

    private fun handleSendComplex(sender: CommandSender, args: Array<String>) {
        if (!sender.hasPermission("mailsystem.command.sendcomplex")) {
            sender.sendMessage("\u00a7cYou don't have permission to send complex mail.")
            return
        }

        if (args.size < 3) {
            sender.sendMessage("\u00a7cUsage: /mail sendcomplex <player|*|@online> <id>")
            return
        }

        val target = args[1]
        val id = args[2]
        val config = complexMailLoader.getConfig(id)

        if (config == null) {
            sender.sendMessage("\u00a7cComplex mail '$id' not found.")
            return
        }

        when (target) {
            "*" -> sendComplexToAll(sender, config)
            "@online" -> sendComplexToOnline(sender, config)
            else -> sendComplexToPlayer(sender, target, config)
        }
    }

    private fun sendComplexToAll(sender: CommandSender, config: org.ReDiego0.mailSystem.config.ComplexMailConfig) {
        manager.getAllProfileUUIDs().thenAcceptAsync({ uuids ->
            val targets = if (config.requiresOnline) {
                uuids.filter { Bukkit.getPlayer(it)?.isOnline == true }
            } else {
                uuids
            }
            for (uuid in targets) {
                manager.deliverMail(config.buildMail(uuid))
            }
            sender.sendMessage("\u00a7aSent complex mail '${config.id}' to ${targets.size} player(s).")
        }, mainExecutor)
    }

    private fun sendComplexToOnline(sender: CommandSender, config: org.ReDiego0.mailSystem.config.ComplexMailConfig) {
        val onlinePlayers = Bukkit.getOnlinePlayers()
        for (player in onlinePlayers) {
            manager.deliverMail(config.buildMail(player.uniqueId))
        }
        sender.sendMessage("\u00a7aSent complex mail '${config.id}' to ${onlinePlayers.size} player(s).")
    }

    private fun sendComplexToPlayer(sender: CommandSender, playerName: String, config: org.ReDiego0.mailSystem.config.ComplexMailConfig) {
        val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline) {
            sender.sendMessage("\u00a7cPlayer '$playerName' has never played on this server.")
            return
        }
        if (config.requiresOnline && !offlinePlayer.isOnline) {
            sender.sendMessage("\u00a7cPlayer '$playerName' must be online for this mail.")
            return
        }
        manager.deliverMail(config.buildMail(offlinePlayer.uniqueId))
        sender.sendMessage("\u00a7aSent complex mail '${config.id}' to $playerName.")
    }
}
