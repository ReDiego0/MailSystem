package org.ReDiego0.mailSystem.storage

import org.bukkit.inventory.ItemStack
import java.util.Base64

object ItemSerializer {

    private val ENCODER = Base64.getEncoder()
    private val DECODER = Base64.getDecoder()

    fun serialize(item: ItemStack): String =
        ENCODER.encodeToString(item.serializeAsBytes())

    fun deserialize(data: String): ItemStack =
        ItemStack.deserializeBytes(DECODER.decode(data))
}