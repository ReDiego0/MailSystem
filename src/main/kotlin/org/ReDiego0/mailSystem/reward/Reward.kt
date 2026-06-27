package org.ReDiego0.mailSystem.reward

import org.bukkit.inventory.ItemStack

sealed interface Reward {
    val displayItem: ItemStack
}
