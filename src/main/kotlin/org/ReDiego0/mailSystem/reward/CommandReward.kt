package org.ReDiego0.mailSystem.reward

import org.bukkit.inventory.ItemStack

data class CommandReward(
    override val displayItem: ItemStack,
    val commands: List<String>
) : Reward
