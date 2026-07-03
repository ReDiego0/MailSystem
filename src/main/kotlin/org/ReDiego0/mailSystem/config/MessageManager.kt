package org.ReDiego0.mailSystem.config

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class MessageManager(private val plugin: JavaPlugin) {

    private lateinit var config: YamlConfiguration

    fun load() {
        val file = File(plugin.dataFolder, "messages.yml")
        if (!file.exists()) {
            plugin.dataFolder.mkdirs()
            generateDefault(file)
        }
        config = YamlConfiguration.loadConfiguration(file)
    }

    fun get(path: String, vararg placeholders: Pair<String, Any>): Component {
        val raw = config.getString(path) ?: return Component.text(path, NamedTextColor.RED)
        var processed = raw
        for ((key, value) in placeholders) {
            processed = processed.replace("%$key%", value.toString())
        }
        return LEGACY.deserialize(processed)
    }

    fun getRaw(path: String, vararg placeholders: Pair<String, Any>): String {
        val raw = config.getString(path) ?: return path
        var processed = raw
        for ((key, value) in placeholders) {
            processed = processed.replace("%$key%", value.toString())
        }
        return processed
    }

    fun sendMessage(sender: CommandSender, path: String, vararg placeholders: Pair<String, Any>) {
        sender.sendMessage(get(path, *placeholders))
    }

    fun playSound(player: Player, path: String) {
        val soundName = config.getString("sounds.$path") ?: return
        try {
            val sound = Sound.valueOf(soundName)
            player.playSound(player.location, sound, 1f, 1f)
        } catch (_: IllegalArgumentException) {
            plugin.logger.warning("Invalid sound in messages.yml: $soundName")
        }
    }

    private fun generateDefault(file: File) {
        val yaml = YamlConfiguration()

        yaml.set("messages.gui.title", "&8Mail Inbox")
        yaml.set("messages.gui.no_mail_selected", "&7No mail selected")
        yaml.set("messages.gui.empty_slot", "&7Empty")
        yaml.set("messages.gui.unread", "&a&l● &aUnread")
        yaml.set("messages.gui.read", "&e&l● &eRead")
        yaml.set("messages.gui.claimed", "&8&l● &8Claimed")
        yaml.set("messages.gui.rewards_pending", "&6&l♔ %count% reward(s) pending")
        yaml.set("messages.gui.click_to_select", "&eClick to select")
        yaml.set("messages.gui.previous_page", "&ePrevious Page")
        yaml.set("messages.gui.next_page", "&eNext Page")
        yaml.set("messages.gui.refresh", "&aRefresh Inbox")
        yaml.set("messages.gui.claim_rewards", "&6Claim Rewards")
        yaml.set("messages.gui.delete_mail", "&cDelete Mail")
        yaml.set("messages.gui.delete_confirm", "&c&lClick again to confirm")
        yaml.set("messages.gui.clear_all_read", "&4Clear All Read")
        yaml.set("messages.gui.close", "&cClose")
        yaml.set("messages.gui.page_indicator", "&7Page &f%current%&7/&f%total%")
        yaml.set("messages.gui.total_mails", "&7Total mails: &f%count%")
        yaml.set("messages.gui.unavailable", "&7Unavailable")
        yaml.set("messages.gui.letter", "&fLetter")
        yaml.set("messages.gui.no_content", "&7(No content)")
        yaml.set("messages.gui.command_reward", "&6Command Reward")
        yaml.set("messages.gui.commands_label", "&7Commands:")
        yaml.set("messages.gui.sender_lore_date", "&7Sent: &f%date%")
        yaml.set("messages.gui.sender_lore_expires", "&7Expires: &f%date%")
        yaml.set("messages.gui.mail_from", "&7From: &f%sender%")
        yaml.set("messages.gui.mail_date", "&7Date: &f%date%")
        yaml.set("messages.gui.select_mail_first", "&7Select a mail first")
        yaml.set("messages.gui.no_rewards_info", "&7Select a mail with rewards")
        yaml.set("messages.gui.click_to_claim", "&eClick to claim all rewards")
        yaml.set("messages.gui.mail_must_be_read", "&7Mail must be read first")
        yaml.set("messages.gui.claim_before_deleting", "&7Claim rewards before deleting")
        yaml.set("messages.gui.click_to_delete", "&eClick to delete this mail")
        yaml.set("messages.gui.no_read_mails", "&7No read mails to clear")
        yaml.set("messages.gui.deletes_all_read", "&eDeletes all read mails")
        yaml.set("messages.gui.click_to_confirm_delete", "&cClick delete again within 3 seconds to confirm")

        yaml.set("messages.notifications.new_mail", "&eYou have new mail! Open your inbox with &6/mail")
        yaml.set("messages.notifications.mail_sent", "&aSent mail to %count% player(s).")
        yaml.set("messages.notifications.mail_sent_to", "&aSent mail to %player%.")
        yaml.set("messages.notifications.complex_sent", "&aSent complex mail '%id%' to %count% player(s).")
        yaml.set("messages.notifications.complex_sent_to", "&aSent complex mail '%id%' to %player%.")
        yaml.set("messages.notifications.rewards_claimed", "&aRewards claimed!")
        yaml.set("messages.notifications.mail_deleted", "&aMail deleted.")
        yaml.set("messages.notifications.read_deleted", "&aDeleted %count% read mail(s).")
        yaml.set("messages.notifications.no_read_mails", "&7No read mails to delete.")

        yaml.set("messages.errors.inventory_full", "&cYour inventory is full!")
        yaml.set("messages.errors.already_claimed", "&cRewards already claimed!")
        yaml.set("messages.errors.mail_not_found", "&cMail not found!")
        yaml.set("messages.errors.expired", "&cThis mail has expired!")
        yaml.set("messages.errors.no_rewards", "&cThis mail has no rewards!")
        yaml.set("messages.errors.command_failed", "&cCommand failed: %command%")
        yaml.set("messages.errors.select_mail_first", "&cSelect a mail first.")
        yaml.set("messages.errors.must_be_read", "&cMail must be read before deleting.")
        yaml.set("messages.errors.claim_before_delete", "&cClaim rewards before deleting.")
        yaml.set("messages.errors.delete_failed", "&cFailed to delete mail.")
        yaml.set("messages.errors.player_not_found", "&cPlayer '%player%' has never played on this server.")
        yaml.set("messages.errors.player_must_be_online", "&cPlayer '%player%' must be online for this mail.")
        yaml.set("messages.errors.conditions_not_met", "&cPlayer '%player%' does not meet the conditions for this mail.")
        yaml.set("messages.errors.complex_not_found", "&cComplex mail '%id%' not found.")
        yaml.set("messages.errors.only_players_gui", "&cOnly players can open the mail GUI.")
        yaml.set("messages.errors.no_permission", "&cYou don't have permission to send mail.")
        yaml.set("messages.errors.no_permission_complex", "&cYou don't have permission to send complex mail.")
        yaml.set("messages.errors.usage_send", "&cUsage: /mail send <player|*|@online> <title|line2|line3>")
        yaml.set("messages.errors.usage_sendcomplex", "&cUsage: /mail sendcomplex <player|*|@online> <id>")
        yaml.set("messages.errors.title_empty", "&cTitle cannot be empty.")
        yaml.set("messages.errors.unknown_subcommand", "&cUnknown subcommand: %subcommand%")

        yaml.set("sounds.open_gui", "BLOCK_CHEST_OPEN")
        yaml.set("sounds.turn_page", "ITEM_BOOK_PAGE_TURN")
        yaml.set("sounds.select_mail", "ITEM_BOOK_PAGE_TURN")
        yaml.set("sounds.claim_success", "ENTITY_PLAYER_LEVELUP")
        yaml.set("sounds.claim_error", "ENTITY_VILLAGER_NO")
        yaml.set("sounds.delete_mail", "BLOCK_ANVIL_BREAK")
        yaml.set("sounds.close_gui", "BLOCK_CHEST_CLOSE")

        yaml.save(file)
        plugin.logger.info("Generated default messages.yml")
    }

    companion object {
        private val LEGACY = LegacyComponentSerializer.legacyAmpersand()
    }
}
