package org.ReDiego0.mailSystem.gui

import org.ReDiego0.mailSystem.config.MessageManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent

class MailGuiListener(
    private val gui: MailGui,
    private val msg: MessageManager
) : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title() != GuiConstants.TITLE_COMPONENT) return
        event.isCancelled = true

        val slot = event.rawSlot
        if (slot < 0 || slot >= GuiConstants.INVENTORY_SIZE) return

        val player = event.whoClicked as? Player ?: return
        gui.handleClick(player, slot)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.view.title() != GuiConstants.TITLE_COMPONENT) return
        val player = event.player as? Player ?: return
        gui.handleClose(player)
    }
}
