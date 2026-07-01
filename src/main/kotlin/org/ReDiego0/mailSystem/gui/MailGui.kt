package org.ReDiego0.mailSystem.gui

import org.ReDiego0.mailSystem.manager.ClaimResult
import org.ReDiego0.mailSystem.manager.MailManager
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MailGui(
    private val plugin: JavaPlugin,
    private val manager: MailManager
) {

    private val openStates = HashMap<UUID, GuiState>()
    private val mainExecutor = Executor { Bukkit.getScheduler().runTask(plugin, it) }
    private val asyncExecutor: ExecutorService = Executors.newCachedThreadPool()

    fun open(player: Player) {
        manager.loadProfile(player.uniqueId).thenAcceptAsync({ profile ->
            val mails = profile?.mails?.toList() ?: emptyList()
            val state = GuiState(player.uniqueId, mails = mails)
            openStates[player.uniqueId] = state
            val inventory = buildInventory(state)
            player.openInventory(inventory)
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
            slot in GuiConstants.MAIL_ICON_SLOTS -> handleMailClick(player, state, slot)
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
    }

    fun isOpen(player: Player): Boolean = openStates.containsKey(player.uniqueId)

    // ── Inventory Build ──────────────────────────────────────────────────

    private fun buildInventory(state: GuiState): Inventory {
        val inventory = Bukkit.createInventory(null, GuiConstants.INVENTORY_SIZE, GuiConstants.TITLE)
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
            for (s in rowSlots) {
                inventory.setItem(s, createFillerItem())
            }

            if (i < pageMails.size) {
                val mail = pageMails[i]
                inventory.setItem(GuiConstants.MAIL_ICON_SLOTS[i], createMailIcon(mail, mail.id == state.selectedMailId))
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

        meta.setDisplayName(
            when (mail.status) {
                MailStatus.UNREAD -> "\u00a7e\u00a7l${mail.title}"
                MailStatus.READ -> "\u00a7f${mail.title}"
                MailStatus.CLAIMED -> "\u00a77${mail.title}"
            }
        )

        val lore = mutableListOf<String>()
        lore.add("\u00a77From: \u00a7f${mail.senderName}")
        lore.add("\u00a77Date: \u00a7f${DATE_FORMAT.format(Date(mail.createdAt))}")
        lore.add("")
        lore.add(
            when (mail.status) {
                MailStatus.UNREAD -> "\u00a7a\u25cf Unread"
                MailStatus.READ -> "\u00a7e\u25cf Read"
                MailStatus.CLAIMED -> "\u00a78\u25cf Claimed"
            }
        )
        if (mail.rewards.isNotEmpty()) {
            val unclaimed = mail.rewards.size
            lore.add("\u00a76\u26c1 $unclaimed reward(s) pending")
        }
        lore.add("")
        lore.add("\u00a7eClick to select")
        meta.lore = lore

        if (selected) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true)
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }

        if (mail.status == MailStatus.CLAIMED) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        }

        item.itemMeta = meta
        return item
    }

    // ── Zone B: Content Viewer ───────────────────────────────────────────

    private fun fillContentViewer(inventory: Inventory, state: GuiState) {
        val mail = state.selectedMail

        inventory.setItem(GuiConstants.SENDER_SLOT, if (mail != null) createSenderHead(mail) else createEmptySlot(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "\u00a77No mail selected"))

        for (slot in GuiConstants.TEXT_SLOTS) {
            inventory.setItem(slot, if (mail != null) createTextItem(mail) else createEmptySlot(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "\u00a77---"))
        }

        for ((index, slot) in GuiConstants.REWARD_SLOTS.withIndex()) {
            inventory.setItem(slot, if (mail != null && index < mail.rewards.size) {
                createRewardItem(mail.rewards[index])
            } else {
                createEmptySlot(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "\u00a77Empty")
            })
        }
    }

    private fun createSenderHead(mail: Mail): ItemStack {
        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta ?: return head

        meta.setDisplayName("\u00a76${mail.senderName}")

        val lore = mutableListOf<String>()
        lore.add("\u00a77Sent: \u00a7f${DATE_FORMAT.format(Date(mail.createdAt))}")
        lore.add("\u00a77Expires: \u00a7f${DATE_FORMAT.format(Date(mail.expiresAt))}")
        lore.add("")
        lore.add("\u00a7e${mail.title}")
        meta.lore = lore

        head.itemMeta = meta
        return head
    }

    private fun createTextItem(mail: Mail): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta ?: return item

        meta.setDisplayName("\u00a7fLetter")

        val lore = mutableListOf<String>()
        lore.add("\u00a77\u00a7o${mail.title}")
        lore.add("")
        for (line in mail.body) {
            lore.add("\u00a77$line")
        }
        if (mail.body.isEmpty()) {
            lore.add("\u00a77(No content)")
        }
        meta.lore = lore

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
                    meta.setDisplayName("\u00a76Command Reward")
                }

                val lore = meta.lore?.toMutableList() ?: mutableListOf()
                lore.add("")
                lore.add("\u00a77Commands:")
                for (cmd in reward.commands) {
                    lore.add("\u00a78- \u00a7f$cmd")
                }
                meta.lore = lore
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

        inventory.setItem(GuiConstants.PREV_PAGE_SLOT, createNavButton(Material.ARROW, "\u00a7ePrevious Page", hasPrev))
        inventory.setItem(GuiConstants.NEXT_PAGE_SLOT, createNavButton(Material.ARROW, "\u00a7eNext Page", hasNext))
        inventory.setItem(GuiConstants.PAGE_INDICATOR_SLOT, createPageIndicator(state))
        inventory.setItem(GuiConstants.REFRESH_SLOT, createButton(Material.CLOCK, "\u00a7aRefresh Inbox"))
        inventory.setItem(GuiConstants.CLAIM_SLOT, createClaimButton(selected))
        inventory.setItem(GuiConstants.DELETE_SLOT, createDeleteButton(selected))
        inventory.setItem(GuiConstants.CLEAR_ALL_SLOT, createClearAllButton(hasMails))
        inventory.setItem(GuiConstants.CLOSE_SLOT, createButton(Material.BARRIER, "\u00a7cClose"))
    }

    private fun createNavButton(material: Material, name: String, enabled: Boolean): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(if (enabled) name else "\u00a78$name")
        if (!enabled) {
            meta.lore = listOf("\u00a77Unavailable")
        }
        item.itemMeta = meta
        return item
    }

    private fun createPageIndicator(state: GuiState): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName("\u00a77Page \u00a7f${state.currentPage + 1}\u00a77/\u00a7f${state.totalPages}")
        meta.lore = listOf("\u00a77Total mails: \u00a7f${state.mails.size}")
        item.itemMeta = meta
        return item
    }

    private fun createClaimButton(mail: Mail?): ItemStack {
        val enabled = mail != null && mail.rewards.isNotEmpty() && mail.status != MailStatus.CLAIMED
        val item = ItemStack(if (enabled) Material.CHEST else Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta ?: return item

        meta.setDisplayName(if (enabled) "\u00a76Claim Rewards" else "\u00a78Claim Rewards")

        if (enabled) {
            meta.lore = listOf("\u00a7eClick to claim all rewards")
        } else {
            meta.lore = listOf("\u00a77Select a mail with rewards")
        }

        item.itemMeta = meta
        return item
    }

    private fun createDeleteButton(mail: Mail?): ItemStack {
        val canDelete = mail != null
                && mail.status == MailStatus.READ
                && mail.rewards.isEmpty()
        val item = ItemStack(if (canDelete) Material.LAVA_BUCKET else Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta ?: return item

        meta.setDisplayName(if (canDelete) "\u00a7cDelete Mail" else "\u00a78Delete Mail")

        if (mail != null && !canDelete) {
            val reasons = mutableListOf<String>()
            if (mail.status != MailStatus.READ) reasons.add("\u00a77Mail must be read first")
            if (mail.rewards.isNotEmpty()) reasons.add("\u00a77Claim rewards before deleting")
            meta.lore = reasons
        } else if (mail == null) {
            meta.lore = listOf("\u00a77Select a mail first")
        } else {
            meta.lore = listOf("\u00a7eClick to delete this mail")
        }

        item.itemMeta = meta
        return item
    }

    private fun createClearAllButton(hasReadMails: Boolean): ItemStack {
        val item = ItemStack(if (hasReadMails) Material.TNT else Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(if (hasReadMails) "\u00a74Clear All Read" else "\u00a78Clear All Read")
        meta.lore = if (hasReadMails) listOf("\u00a7eDeletes all read mails") else listOf("\u00a77No read mails to clear")
        item.itemMeta = meta
        return item
    }

    private fun createButton(material: Material, name: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(name)
        item.itemMeta = meta
        return item
    }

    private fun createEmptySlot(material: Material, name: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(name)
        item.itemMeta = meta
        return item
    }

    private fun createFillerItem(): ItemStack {
        val item = ItemStack(GuiConstants.FILLER_MATERIAL)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(" ")
        item.itemMeta = meta
        return item
    }

    // ── Click Handlers ───────────────────────────────────────────────────

    private fun handleMailClick(player: Player, state: GuiState, slot: Int) {
        val index = GuiConstants.MAIL_ICON_SLOTS.indexOf(slot)
        if (index == -1) return
        val pageMails = state.pageMails
        if (index >= pageMails.size) return

        state.selectMail(pageMails[index].id)

        val topInventory = player.openInventory.topInventory
        fillInventory(topInventory, state)
    }

    private fun handlePrevPage(player: Player, state: GuiState) {
        if (state.currentPage <= 0) return
        state.currentPage--
        state.selectedMailId = null
        val topInventory = player.openInventory.topInventory
        fillInventory(topInventory, state)
    }

    private fun handleNextPage(player: Player, state: GuiState) {
        if (state.currentPage + 1 >= state.totalPages) return
        state.currentPage++
        state.selectedMailId = null
        val topInventory = player.openInventory.topInventory
        fillInventory(topInventory, state)
    }

    private fun handleRefresh(player: Player) {
        refresh(player)
    }

    private fun handleClaim(player: Player, state: GuiState) {
        val mail = state.selectedMail
        if (mail == null) {
            player.sendMessage("\u00a7cSelect a mail first.")
            return
        }
        if (mail.rewards.isEmpty()) {
            player.sendMessage("\u00a7cThis mail has no rewards.")
            return
        }
        if (mail.status == MailStatus.CLAIMED) {
            player.sendMessage("\u00a7cRewards already claimed.")
            return
        }

        manager.claimRewards(mail.id, player).thenAcceptAsync({ result ->
            when (result) {
                ClaimResult.Success -> {
                    player.sendMessage("\u00a7aRewards claimed!")
                    refresh(player)
                }
                ClaimResult.InventoryFull -> player.sendMessage("\u00a7cYour inventory is full!")
                ClaimResult.AlreadyClaimed -> player.sendMessage("\u00a7cRewards already claimed!")
                ClaimResult.MailNotFound -> player.sendMessage("\u00a7cMail not found!")
                ClaimResult.Expired -> player.sendMessage("\u00a7cThis mail has expired!")
                ClaimResult.NoRewards -> player.sendMessage("\u00a7cThis mail has no rewards!")
                is ClaimResult.CommandFailed -> player.sendMessage("\u00a7cCommand failed: ${result.command}")
            }
        }, mainExecutor)
    }

    private fun handleDelete(player: Player, state: GuiState) {
        val mail = state.selectedMail
        if (mail == null) {
            player.sendMessage("\u00a7cSelect a mail first.")
            return
        }
        if (mail.status != MailStatus.READ) {
            player.sendMessage("\u00a7cMail must be read before deleting.")
            return
        }
        if (mail.rewards.isNotEmpty()) {
            player.sendMessage("\u00a7cClaim rewards before deleting.")
            return
        }

        manager.deleteMail(mail.id, player.uniqueId).thenAcceptAsync({ deleted ->
            if (deleted) {
                player.sendMessage("\u00a7aMail deleted.")
                refresh(player)
            } else {
                player.sendMessage("\u00a7cFailed to delete mail.")
            }
        }, mainExecutor)
    }

    private fun handleClearAll(player: Player) {
        manager.deleteAllRead(player.uniqueId).thenAcceptAsync({ count ->
            if (count > 0) {
                player.sendMessage("\u00a7aDeleted $count read mail(s).")
                refresh(player)
            } else {
                player.sendMessage("\u00a77No read mails to delete.")
            }
        }, mainExecutor)
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm")
    }
}
