package org.ReDiego0.mailSystem.gui

import org.bukkit.Material

object GuiConstants {

    const val INVENTORY_SIZE = 54
    const val TITLE = "\u00a78Mail Inbox"
    const val MAILS_PER_PAGE = 5

    val MAIL_ICON_SLOTS = intArrayOf(0, 9, 18, 27, 36)
    val MAIL_ROW_SLOTS = arrayOf(
        intArrayOf(0, 1, 2, 3),
        intArrayOf(9, 10, 11, 12),
        intArrayOf(18, 19, 20, 21),
        intArrayOf(27, 28, 29, 30),
        intArrayOf(36, 37, 38, 39)
    )

    const val SENDER_SLOT = 4

    val TEXT_SLOTS = intArrayOf(14, 15, 16, 23, 24, 25)
    val REWARD_SLOTS = intArrayOf(31, 32, 33, 34, 35, 40, 41, 42, 43, 44)

    const val PREV_PAGE_SLOT = 45
    const val NEXT_PAGE_SLOT = 46
    const val PAGE_INDICATOR_SLOT = 47
    const val REFRESH_SLOT = 48
    const val CLAIM_SLOT = 49
    const val DELETE_SLOT = 50
    const val CLEAR_ALL_SLOT = 52
    const val CLOSE_SLOT = 53

    val ALL_GUI_SLOTS = (0 until INVENTORY_SIZE).toList()

    val FILLER_SLOTS: Set<Int> by lazy {
        val occupied = mutableSetOf<Int>()
        occupied.addAll(MAIL_ICON_SLOTS.toList())
        for (row in MAIL_ROW_SLOTS) occupied.addAll(row.toList())
        occupied.add(SENDER_SLOT)
        occupied.addAll(TEXT_SLOTS.toList())
        occupied.addAll(REWARD_SLOTS.toList())
        occupied.addAll(intArrayOf(PREV_PAGE_SLOT, NEXT_PAGE_SLOT, PAGE_INDICATOR_SLOT, REFRESH_SLOT, CLAIM_SLOT, DELETE_SLOT, CLEAR_ALL_SLOT, CLOSE_SLOT).toList())
        (0 until INVENTORY_SIZE).filter { it !in occupied }.toSet()
    }

    val FILLER_MATERIAL = Material.BLACK_STAINED_GLASS_PANE
    val DIVIDER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE
}
