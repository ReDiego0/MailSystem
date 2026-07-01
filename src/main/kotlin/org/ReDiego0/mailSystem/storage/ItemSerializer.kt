package org.ReDiego0.mailSystem.storage

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack

object ItemSerializer {

    fun serialize(item: ItemStack): String {
        val config = YamlConfiguration()
        config.set("i", item)
        return config.saveToString()
    }

    fun deserialize(data: String): ItemStack {
        val config = YamlConfiguration()
        config.loadFromString(data)
        return config.getItemStack("i")!!
    }
}
