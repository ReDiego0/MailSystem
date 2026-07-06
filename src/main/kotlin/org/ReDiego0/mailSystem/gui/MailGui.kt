package org.ReDiego0.mailSystem.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import org.ReDiego0.mailSystem.config.MessageManager
import org.ReDiego0.mailSystem.manager.ClaimResult
import org.ReDiego0.mailSystem.manager.MailManager
import org.ReDiego0.mailSystem.manager.SimpleMailManager
import org.ReDiego0.mailSystem.model.Mail
import org.ReDiego0.mailSystem.model.MailStatus
import org.ReDiego0.mailSystem.reward.CommandReward
import org.ReDiego0.mailSystem.reward.PhysicalReward
import org.ReDiego0.mailSystem.reward.Reward
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

class MailGui(
    private val plugin: JavaPlugin,
    private val manager: MailManager,
    private val msg: MessageManager
) {

    private val openStates = ConcurrentHashMap<UUID, GuiState>()
    private val deleteConfirmations = ConcurrentHashMap<UUID, Long>()
    private val mainExecutor = Executor { Bukkit.getScheduler().runTask(plugin, it) }

    fun open(player: Player) {
        manager.loadProfile(player.uniqueId).thenAcceptAsync({ profile ->
            val mails = profile?.mails?.toList() ?: emptyList()
            val state = GuiState(player.uniqueId, mails = mails)
            openStates[player.uniqueId] = state
            val inventory = buildInventory(state)
            player.openInventory(inventory)
            msg.playSound(player, "open_gui")
        }, mainExecutor)
    }

    fun refresh(player: Player) {
        val state = openStates[player.uniqueId] ?: return
        val topInventory = player.openInventory.topInventory
        if (topInventory.size != GuiConstants.INVENTORY_SIZE) return

        manager.loadProfile(player.uniqueId).thenAcceptAsync({ profile ->
            state.mails = profile?.mails?.toList() ?: emptyList()
            if (state.currentPage >= state.totalPages) {
                state.currentPage = maxOf(0, state.totalPages - 1)
            }
            state.selectedMailId = null
            fillInventory(topInventory, state)
        }, mainExecutor)
    }

    fun handleClick(player: Player, slot: Int) {
        val state = openStates[player.uniqueId] ?: return
        when {
            GuiConstants.MAIL_ROW_SLOTS.any { slot in it } -> handleMailClick(player, state, slot)
            slot == GuiConstants.PREV_PAGE_SLOT -> handlePrevPage(player, state)
            slot == GuiConstants.NEXT_PAGE_SLOT -> handleNextPage(player, state)
            slot == GuiConstants.REFRESH_SLOT -> handleRefresh(player)
            slot == GuiConstants.CLAIM_SLOT -> handleClaim(player, state)
            slot == GuiConstants.DELETE_SLOT -> handleDelete(player, state)
            slot == GuiConstants.CLEAR_ALL_SLOT -> handleClearAll(player)
            slot == GuiConstants.CLOSE_SLOT -> player.closeInventory()
        }
    }

    fun handleClose(player: Player) {
        openStates.remove(player.uniqueId)
        deleteConfirmations.remove(player.uniqueId)
        msg.playSound(player, "close_gui")
    }

    fun isOpen(player: Player): Boolean = openStates.containsKey(player.uniqueId)

    // ── Inventory Build ──────────────────────────────────────────────────

    private fun buildInventory(state: GuiState): Inventory {
        val inventory = Bukkit.createInventory(null, GuiConstants.INVENTORY_SIZE, GuiConstants.TITLE_COMPONENT)
        fillInventory(inventory, state)
        return inventory
    }

    private fun fillInventory(inventory: Inventory, state: GuiState) {
        inventory.clear()

        for (slot in GuiConstants.FILLER_SLOTS) {
            inventory.setItem(slot, createFillerItem())
        }

        fillMailList(inventory, state)
        fillContentViewer(inventory, state)
        fillControls(inventory, state)
    }

    // ── Zone A: Mail List ────────────────────────────────────────────────

    private fun fillMailList(inventory: Inventory, state: GuiState) {
        val pageMails = state.pageMails
        for (i in GuiConstants.MAIL_ICON_SLOTS.indices) {
            val rowSlots = GuiConstants.MAIL_ROW_SLOTS[i]

            if (i < pageMails.size) {
                val mail = pageMails[i]
                val icon = createMailIcon(mail, mail.id == state.selectedMailId)
                for (s in rowSlots) {
                    inventory.setItem(s, icon)
                }
            } else {
                for (s in rowSlots) {
                    inventory.setItem(s, createFillerItem())
                }
            }
        }
    }

    private fun createMailIcon(mail: Mail, selected: Boolean): ItemStack {
        val material = when (mail.status) {
            MailStatus.UNREAD -> Material.ENCHANTED_BOOK
            MailStatus.READ -> Material.PAPER
            MailStatus.CLAIMED -> Material.MAP
        }
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        val titleColor = when (mail.status) {
            MailStatus.UNREAD -> Style.style(NamedTextColor.YELLOW, TextDecoration.BOLD)
            MailStatus.READ -> Style.style(NamedTextColor.WHITE)
            MailStatus.CLAIMED -> Style.style(NamedTextColor.GRAY)
        }
        meta.displayName(Component.text(mail.title, titleColor))

        val lore = mutableListOf<Component>()
        lore.add(msg.get("gui.mail_from", "sender" to mail.senderName))
        lore.add(msg.get("gui.mail_date", "date" to DATE_FORMAT.format(Date(mail.createdAt))))
        lore.add(Component.empty())
        lore.add(msg.get("gui.${mail.status.name.lowercase()}"))
        if (mail.rewards.isNotEmpty()) {
            lore.add(msg.get("gui.rewards_pending", "count" to mail.rewards.size.toString()))
        }
        lore.add(Component.empty())
        lore.add(msg.get("gui.click_to_select"))
        meta.lore(lore)

        if (selected) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true)
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }

        item.itemMeta = meta
        return item
    }

    // ── Zone B: Content Viewer ───────────────────────────────────────────

    private fun fillContentViewer(inventory: Inventory, state: GuiState) {
        val mail = state.selectedMail
        val noMailComponent = msg.get("gui.no_mail_selected")

        inventory.setItem(GuiConstants.SENDER_SLOT, if (mail != null) createSenderHead(mail) else createEmptySlot(Material.LIGHT_GRAY_STAINED_GLASS_PANE, noMailComponent))

        for (slot in GuiConstants.TEXT_SLOTS) {
            inventory.setItem(slot, if (mail != null) createTextItem(mail) else createEmptySlot(Material.LIGHT_GRAY_STAINED_GLASS_PANE, noMailComponent))
        }

        for ((index, slot) in GuiConstants.REWARD_SLOTS.withIndex()) {
            inventory.setItem(slot, if (mail != null && index < mail.rewards.size) {
                createRewardItem(mail.rewards[index])
            } else {
                createEmptySlot(Material.LIGHT_GRAY_STAINED_GLASS_PANE, noMailComponent)
            })
        }

        for (slot in GuiConstants.ZONE_B_EMPTY_SLOTS) {
            inventory.setItem(slot, createEmptySlot(Material.LIGHT_GRAY_STAINED_GLASS_PANE, noMailComponent))
        }
    }

    private fun createSenderHead(mail: Mail): ItemStack {
        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta ?: return head

        meta.displayName(Component.text(mail.senderName, NamedTextColor.GOLD))

        val lore = mutableListOf<Component>()
        lore.add(msg.get("gui.sender_lore_date", "date" to DATE_FORMAT.format(Date(mail.createdAt))))
        lore.add(msg.get("gui.sender_lore_expires", "date" to DATE_FORMAT.format(Date(mail.expiresAt))))
        lore.add(Component.empty())
        lore.add(Component.text(mail.title, NamedTextColor.YELLOW))
        meta.lore(lore)

        head.itemMeta = meta
        return head
    }

    private fun createTextItem(mail: Mail): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta ?: return item

        meta.displayName(msg.get("gui.letter"))

        val lore = mutableListOf<Component>()
        lore.add(Component.text(mail.title, Style.style(NamedTextColor.GRAY, TextDecoration.ITALIC)))
        lore.add(Component.empty())
        for (line in mail.body) {
            lore.add(Component.text(line, NamedTextColor.GRAY))
        }
        if (mail.body.isEmpty()) {
            lore.add(msg.get("gui.no_content"))
        }
        meta.lore(lore)

        item.itemMeta = meta
        return item
    }

    private fun createRewardItem(reward: Reward): ItemStack {
        return when (reward) {
            is PhysicalReward -> reward.item.clone()
            is CommandReward -> {
                val item = reward.displayItem.clone()
                val meta = item.itemMeta ?: return item

                if (meta.displayName() == null) {
                    meta.displayName(msg.get("gui.command_reward"))
                }

                val lore = meta.lore()?.toMutableList() ?: mutableListOf()
                lore.add(Component.empty())
                lore.add(msg.get("gui.commands_label"))
                for (cmd in reward.commands) {
                    lore.add(Component.text("- $cmd", Style.style(NamedTextColor.DARK_GRAY, NamedTextColor.WHITE)))
                }
                meta.lore(lore)
                item.itemMeta = meta
                item
            }
        }
    }

    // ── Zone C: Controls ─────────────────────────────────────────────────

    private fun fillControls(inventory: Inventory, state: GuiState) {
        val hasMails = state.mails.isNotEmpty()
        val hasPrev = state.currentPage > 0
        val hasNext = (state.currentPage + 1) < state.totalPages
        val selected = state.selectedMail

        inventory.setItem(GuiConstants.PREV_PAGE_SLOT, createNavButton(Material.ARROW, msg.get("gui.previous_page"), hasPrev))
        inventory.setItem(GuiConstants.NEXT_PAGE_SLOT, createNavButton(Material.ARROW, msg.get("gui.next_page"), hasNext))
        inventory.setItem(GuiConstants.PAGE_INDICATOR_SLOT, createPageIndicator(state))
        inventory.setItem(GuiConstants.REFRESH_SLOT, createButton(Material.CLOCK, msg.get("gui.refresh")))
        inventory.setItem(GuiConstants.CLAIM_SLOT, createClaimButton(selected))
        inventory.setItem(GuiConstants.DELETE_SLOT, createDeleteButton(selected))
        inventory.setItem(GuiConstants.CLEAR_ALL_SLOT, createClearAllButton(hasMails))
        inventory.setItem(GuiConstants.CLOSE_SLOT, createButton(Material.BARRIER, msg.get("gui.close")))
    }

    private fun createNavButton(material: Material, name: Component, enabled: Boolean): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(if (enabled) name else name.color(NamedTextColor.DARK_GRAY))
        if (!enabled) {
            meta.lore(listOf(msg.get("gui.unavailable")))
        }
        item.itemMeta = meta
        return item
    }

    private fun createPageIndicator(state: GuiState): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta ?: return item
        meta.displayName(msg.get("gui.page_indicator", "current" to (state.currentPage + 1).toString(), "total" to state.totalPages.toString()))
        meta.lore(listOf(msg.get("gui.total_mails", "count" to state.mails.size.toString())))
        item.itemMeta = meta
        return item
    }

    private fun createClaimButton(mail: Mail?): ItemStack {
        val enabled = mail != null && mail.rewards.isNotEmpty() && mail.status != MailStatus.CLAIMED
        val item = ItemStack(if (enabled) Material.CHEST else Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta ?: return item

        meta.displayName(if (enabled) msg.get("gui.claim_rewards") else msg.get("gui.claim_rewards").color(NamedTextColor.DARK_GRAY))

        if (enabled) {
            meta.lore(listOf(msg.get("gui.click_to_claim")))
        } else {
            meta.lore(listOf(msg.get("gui.select_mail_first")))
        }

        item.itemMeta = meta
        return item
    }

    private fun createDeleteButton(mail: Mail?, confirming: Boolean = false): ItemStack {
        val canDelete = mail != null && (
            (mail.status == MailStatus.READ && mail.rewards.isEmpty()) ||
            mail.status == MailStatus.CLAIMED
        )
        val item = ItemStack(if (canDelete) Material.LAVA_BUCKET else Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta ?: return item

        if (confirming && canDelete) {
            meta.displayName(msg.get("gui.delete_confirm"))
            meta.lore(listOf(msg.get("gui.click_to_confirm_delete")))
        } else {
            meta.displayName(if (canDelete) msg.get("gui.delete_mail") else msg.get("gui.delete_mail").color(NamedTextColor.DARK_GRAY))

            if (mail != null && !canDelete) {
                val reasons = mutableListOf<Component>()
                when {
                    mail.status == MailStatus.UNREAD -> reasons.add(msg.get("gui.mail_must_be_read"))
                    mail.status == MailStatus.READ && mail.rewards.isNotEmpty() -> reasons.add(msg.get("gui.claim_before_deleting"))
                }
                meta.lore(reasons)
            } else if (mail == null) {
                meta.lore(listOf(msg.get("gui.select_mail_first")))
            } else {
                meta.lore(listOf(msg.get("gui.click_to_delete")))
            }
        }

        item.itemMeta = meta
        return item
    }

    private fun createClearAllButton(hasReadMails: Boolean): ItemStack {
        val item = ItemStack(if (hasReadMails) Material.TNT else Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta ?: return item
        meta.displayName(if (hasReadMails) msg.get("gui.clear_all_read") else msg.get("gui.clear_all_read").color(NamedTextColor.DARK_GRAY))
        meta.lore(if (hasReadMails) listOf(msg.get("gui.deletes_all_read")) else listOf(msg.get("gui.no_read_mails")))
        item.itemMeta = meta
        return item
    }

    private fun createButton(material: Material, name: Component): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(name)
        item.itemMeta = meta
        return item
    }

    private fun createEmptySlot(material: Material, name: Component): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(name)
        item.itemMeta = meta
        return item
    }

    private fun createFillerItem(): ItemStack {
        val item = ItemStack(GuiConstants.FILLER_MATERIAL)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.empty())
        item.itemMeta = meta
        return item
    }

    // ── Click Handlers ───────────────────────────────────────────────────

    private fun handleMailClick(player: Player, state: GuiState, slot: Int) {
        val rowIndex = GuiConstants.MAIL_ROW_SLOTS.indexOfFirst { slot in it }
        if (rowIndex == -1) return
        val pageMails = state.pageMails
        if (rowIndex >= pageMails.size) return

        val mail = pageMails[rowIndex]

        if (mail.status == MailStatus.UNREAD) {
            state.mails = state.mails.map {
                if (it.id == mail.id) it.copy(status = MailStatus.READ) else it
            }
            (manager as SimpleMailManager).getStorage().updateMailStatus(mail.id, MailStatus.READ)
        }

        state.selectMail(mail.id)
        deleteConfirmations.remove(player.uniqueId)

        val topInventory = player.openInventory.topInventory
        fillInventory(topInventory, state)
        msg.playSound(player, "select_mail")
    }

    private fun handlePrevPage(player: Player, state: GuiState) {
        if (state.currentPage <= 0) return
        state.currentPage--
        state.selectedMailId = null
        deleteConfirmations.remove(player.uniqueId)
        val topInventory = player.openInventory.topInventory
        fillInventory(topInventory, state)
        msg.playSound(player, "turn_page")
    }

    private fun handleNextPage(player: Player, state: GuiState) {
        if (state.currentPage + 1 >= state.totalPages) return
        state.currentPage++
        state.selectedMailId = null
        deleteConfirmations.remove(player.uniqueId)
        val topInventory = player.openInventory.topInventory
        fillInventory(topInventory, state)
        msg.playSound(player, "turn_page")
    }

    private fun handleRefresh(player: Player) {
        refresh(player)
    }

    private fun handleClaim(player: Player, state: GuiState) {
        val mail = state.selectedMail
        if (mail == null) {
            msg.sendMessage(player, "errors.select_mail_first")
            msg.playSound(player, "claim_error")
            return
        }
        if (mail.rewards.isEmpty()) {
            msg.sendMessage(player, "errors.no_rewards")
            msg.playSound(player, "claim_error")
            return
        }
        if (mail.status == MailStatus.CLAIMED) {
            msg.sendMessage(player, "errors.already_claimed")
            msg.playSound(player, "claim_error")
            return
        }

        manager.claimRewards(mail.id, player).thenAcceptAsync({ result ->
            when (result) {
                ClaimResult.Success -> {
                    msg.sendMessage(player, "notifications.rewards_claimed")
                    msg.playSound(player, "claim_success")
                    refresh(player)
                }
                ClaimResult.InventoryFull -> {
                    msg.sendMessage(player, "errors.inventory_full")
                    msg.playSound(player, "claim_error")
                }
                ClaimResult.AlreadyClaimed -> {
                    msg.sendMessage(player, "errors.already_claimed")
                    msg.playSound(player, "claim_error")
                }
                ClaimResult.MailNotFound -> msg.sendMessage(player, "errors.mail_not_found")
                ClaimResult.Expired -> msg.sendMessage(player, "errors.expired")
                ClaimResult.NoRewards -> msg.sendMessage(player, "errors.no_rewards")
                is ClaimResult.CommandFailed -> msg.sendMessage(player, "errors.command_failed", "command" to result.command)
            }
        }, mainExecutor)
    }

    private fun handleDelete(player: Player, state: GuiState) {
        val mail = state.selectedMail
        if (mail == null) {
            msg.sendMessage(player, "errors.select_mail_first")
            return
        }
        if (mail.status == MailStatus.UNREAD) {
            msg.sendMessage(player, "errors.must_be_read")
            return
        }
        if (mail.status == MailStatus.READ && mail.rewards.isNotEmpty()) {
            msg.sendMessage(player, "errors.claim_before_delete")
            return
        }

        val lastClick = deleteConfirmations[player.uniqueId] ?: 0
        val now = System.currentTimeMillis()

        if (now - lastClick > 3000) {
            deleteConfirmations[player.uniqueId] = now
            val topInventory = player.openInventory.topInventory
            topInventory.setItem(GuiConstants.DELETE_SLOT, createDeleteButton(mail, true))
            msg.sendMessage(player, "gui.click_to_confirm_delete")
        } else {
            deleteConfirmations.remove(player.uniqueId)
            manager.deleteMail(mail.id, player.uniqueId).thenAcceptAsync({ deleted ->
                if (deleted) {
                    msg.sendMessage(player, "notifications.mail_deleted")
                    msg.playSound(player, "delete_mail")
                    refresh(player)
                } else {
                    msg.sendMessage(player, "errors.delete_failed")
                }
            }, mainExecutor)
        }
    }

    private fun handleClearAll(player: Player) {
        manager.deleteAllRead(player.uniqueId).thenAcceptAsync({ count ->
            if (count > 0) {
                msg.sendMessage(player, "notifications.read_deleted", "count" to count.toString())
                msg.playSound(player, "delete_mail")
                refresh(player)
            } else {
                msg.sendMessage(player, "notifications.no_read_mails")
            }
        }, mainExecutor)
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm")
    }
}
