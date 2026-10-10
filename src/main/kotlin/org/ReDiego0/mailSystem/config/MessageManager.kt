package org.ReDiego0.mailSystem.config

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
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
        if (config.getString("messages.notifications.new_mail_hint") == null) {
            config.set("messages.notifications.new_mail_hint", "&6[Abrir Buzón]")
            config.save(file)
        }
    }

    fun get(path: String, vararg placeholders: Pair<String, Any>): Component {
        val raw = config.getString("messages.$path") ?: return Component.text(path, NamedTextColor.RED)
        var processed = raw
        for ((key, value) in placeholders) {
            processed = processed.replace("%$key%", value.toString())
        }
        return LEGACY.deserialize(processed)
    }

    fun getRaw(path: String, vararg placeholders: Pair<String, Any>): String {
        val raw = config.getString("messages.$path") ?: return path
        var processed = raw
        for ((key, value) in placeholders) {
            processed = processed.replace("%$key%", value.toString())
        }
        return processed
    }

    fun sendMessage(sender: CommandSender, path: String, vararg placeholders: Pair<String, Any>) {
        sender.sendMessage(get(path, *placeholders))
    }

    fun sendNewMailNotification(player: Player) {
        val base = get("notifications.new_mail")
        val hint = config.getString("messages.notifications.new_mail_hint")
        val component = if (hint == null || hint.isBlank()) {
            base
        } else {
            base.append(Component.space()).append(
                LEGACY.deserialize(hint)
                    .clickEvent(ClickEvent.runCommand("/mail"))
                    .hoverEvent(HoverEvent.showText(Component.text("/mail")))
            )
        }
        player.sendMessage(component)
    }

    fun playSound(player: Player, path: String) {
        val soundName = config.getString("sounds.$path") ?: return
        player.playSound(player.location, convertSoundName(soundName), 1f, 1f)
    }

    private fun convertSoundName(name: String): String {
        if (name.contains(":")) return name
        return "minecraft:" + name.lowercase().replace("_", ".")
    }

    private fun generateDefault(file: File) {
        val yaml = YamlConfiguration()

        yaml.set("messages.gui.title", "&8Buzón de Correo")
        yaml.set("messages.gui.no_mail_selected", "&7Ningún correo seleccionado")
        yaml.set("messages.gui.empty_slot", "&7Vacío")
        yaml.set("messages.gui.unread", "&a&l● &aSin leer")
        yaml.set("messages.gui.read", "&e&l● &eLeído")
        yaml.set("messages.gui.claimed", "&8&l● &8Reclamado")
        yaml.set("messages.gui.rewards_pending", "&6&l♔ %count% recompensa(s) pendiente(s)")
        yaml.set("messages.gui.click_to_select", "&eClic para seleccionar")
        yaml.set("messages.gui.previous_page", "&ePágina Anterior")
        yaml.set("messages.gui.next_page", "&ePágina Siguiente")
        yaml.set("messages.gui.refresh", "&aRefrescar Buzón")
        yaml.set("messages.gui.claim_rewards", "&6Reclamar Recompensas")
        yaml.set("messages.gui.delete_mail", "&cEliminar Correo")
        yaml.set("messages.gui.delete_confirm", "&c&lClic de nuevo para confirmar")
        yaml.set("messages.gui.clear_all_read", "&4Vaciar Leídos")
        yaml.set("messages.gui.close", "&cCerrar")
        yaml.set("messages.gui.page_indicator", "&7Página &f%current%&7/&f%total%")
        yaml.set("messages.gui.total_mails", "&7Correos en total: &f%count%")
        yaml.set("messages.gui.unavailable", "&7No disponible")
        yaml.set("messages.gui.letter", "&fCarta")
        yaml.set("messages.gui.no_content", "&7(Sin contenido)")
        yaml.set("messages.gui.command_reward", "&6Recompensa por comando")
        yaml.set("messages.gui.commands_label", "&7Comandos:")
        yaml.set("messages.gui.sender_lore_date", "&7Enviado: &f%date%")
        yaml.set("messages.gui.sender_lore_expires", "&7Expira: &f%date%")
        yaml.set("messages.gui.mail_from", "&7De: &f%sender%")
        yaml.set("messages.gui.mail_date", "&7Fecha: &f%date%")
        yaml.set("messages.gui.select_mail_first", "&7Selecciona un correo primero")
        yaml.set("messages.gui.no_rewards_info", "&7Selecciona un correo con recompensas")
        yaml.set("messages.gui.click_to_claim", "&eClic para reclamar todas las recompensas")
        yaml.set("messages.gui.mail_must_be_read", "&7El correo debe estar leído primero")
        yaml.set("messages.gui.claim_before_deleting", "&7Reclama las recompensas antes de eliminar")
        yaml.set("messages.gui.click_to_delete", "&eClic para eliminar este correo")
        yaml.set("messages.gui.no_read_mails", "&7No hay correos leídos para vaciar")
        yaml.set("messages.gui.deletes_all_read", "&eElimina todos los correos leídos")
        yaml.set("messages.gui.click_to_confirm_delete", "&cClic en eliminar de nuevo dentro de 3 segundos para confirmar")

        yaml.set("messages.notifications.new_mail", "&e¡Tienes correo nuevo!")
        yaml.set("messages.notifications.new_mail_hint", "&6[Abrir Buzón]")
        yaml.set("messages.notifications.mail_sent", "&aCorreo enviado a %count% jugador(es).")
        yaml.set("messages.notifications.mail_sent_to", "&aCorreo enviado a %player%.")
        yaml.set("messages.notifications.complex_sent", "&aCorreo complejo '%id%' enviado a %count% jugador(es).")
        yaml.set("messages.notifications.complex_sent_to", "&aCorreo complejo '%id%' enviado a %player%.")
        yaml.set("messages.notifications.rewards_claimed", "&a¡Recompensas reclamadas!")
        yaml.set("messages.notifications.mail_deleted", "&aCorreo eliminado.")
        yaml.set("messages.notifications.read_deleted", "&aEliminados %count% correo(s) leído(s).")
        yaml.set("messages.notifications.no_read_mails", "&7No hay correos leídos para eliminar.")

        yaml.set("messages.errors.inventory_full", "&c¡Tu inventario está lleno!")
        yaml.set("messages.errors.already_claimed", "&c¡Recompensas ya reclamadas!")
        yaml.set("messages.errors.mail_not_found", "&c¡Correo no encontrado!")
        yaml.set("messages.errors.expired", "&c¡Este correo ha expirado!")
        yaml.set("messages.errors.no_rewards", "&c¡Este correo no tiene recompensas!")
        yaml.set("messages.errors.command_failed", "&cComando fallido: %command%")
        yaml.set("messages.errors.select_mail_first", "&cSelecciona un correo primero.")
        yaml.set("messages.errors.must_be_read", "&cEl correo debe estar leído antes de eliminar.")
        yaml.set("messages.errors.claim_before_delete", "&cReclama las recompensas antes de eliminar.")
        yaml.set("messages.errors.delete_failed", "&cNo se pudo eliminar el correo.")
        yaml.set("messages.errors.player_not_found", "&cEl jugador '%player%' nunca ha jugado en este servidor.")
        yaml.set("messages.errors.player_must_be_online", "&cEl jugador '%player%' debe estar conectado para este correo.")
        yaml.set("messages.errors.conditions_not_met", "&cEl jugador '%player%' no cumple las condiciones para este correo.")
        yaml.set("messages.errors.complex_not_found", "&cCorreo complejo '%id%' no encontrado.")
        yaml.set("messages.errors.only_players_gui", "&cSolo los jugadores pueden abrir el buzón de correo.")
        yaml.set("messages.errors.no_permission", "&cNo tienes permiso para enviar correo.")
        yaml.set("messages.errors.no_permission_complex", "&cNo tienes permiso para enviar correo complejo.")
        yaml.set("messages.errors.usage_send", "&cUso: /mail send <jugador|*|@online> <título|línea2|línea3>")
        yaml.set("messages.errors.usage_sendcomplex", "&cUso: /mail sendcomplex <jugador|*|@online> <id>")
        yaml.set("messages.errors.title_empty", "&cEl título no puede estar vacío.")
        yaml.set("messages.errors.unknown_subcommand", "&cSubcomando desconocido: %subcommand%")
        yaml.set("messages.errors.usage_purge", "&cUso: /mail purge <jugador>")
        yaml.set("messages.errors.no_permission_purge", "&cNo tienes permiso para purgar correos.")
        yaml.set("messages.errors.no_mails_to_purge", "&cEl jugador '%player%' no tiene correos que purgar.")

        yaml.set("messages.notifications.purge_confirm", "&c¿Estás seguro? Escribe /mail purge %player% de nuevo dentro de 3 segundos para confirmar.")
        yaml.set("messages.notifications.purge_success", "&aCorreos de %player% eliminados (%count% correo(s) eliminado(s)).")

        yaml.set("sounds.open_gui", "minecraft:block.chest.open")
        yaml.set("sounds.turn_page", "minecraft:item.book.page_turn")
        yaml.set("sounds.select_mail", "minecraft:item.book.page_turn")
        yaml.set("sounds.claim_success", "minecraft:entity.player.levelup")
        yaml.set("sounds.claim_error", "minecraft:entity.villager.no")
        yaml.set("sounds.delete_mail", "minecraft:block.anvil.break")
        yaml.set("sounds.close_gui", "minecraft:block.chest.close")

        yaml.save(file)
        plugin.logger.info("Generated default messages.yml")
    }

    companion object {
        private val LEGACY = LegacyComponentSerializer.legacyAmpersand()
    }
}
