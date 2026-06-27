package org.ReDiego0.mailSystem.reward

import org.bukkit.inventory.ItemStack

data class PhysicalReward(
    val item: ItemStack
) : Reward {
    override val displayItem: ItemStack get() = item
}
