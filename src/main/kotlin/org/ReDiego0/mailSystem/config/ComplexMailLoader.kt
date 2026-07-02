package org.ReDiego0.mailSystem.config

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class ComplexMailLoader(private val plugin: JavaPlugin) {

    private val configs = mutableMapOf<String, ComplexMailConfig>()

    fun load() {
        val file = File(plugin.dataFolder, "complex_mails.yml")
        if (!file.exists()) {
            plugin.saveResource("complex_mails.yml", false)
        }

        val yaml = YamlConfiguration.loadConfiguration(file)
        val section = yaml.getConfigurationSection("complex_mails") ?: return

        for (id in section.getKeys(false)) {
            val mailSection = section.getConfigurationSection(id) ?: continue
            try {
                configs[id] = parseConfig(id, mailSection)
            } catch (e: Exception) {
                plugin.logger.warning("Failed to load complex mail '$id': ${e.message}")
            }
        }

        plugin.logger.info("Loaded ${configs.size} complex mail(s)")
    }

    private fun parseConfig(id: String, section: ConfigurationSection): ComplexMailConfig {
        return ComplexMailConfig(
            id = id,
            senderName = section.getString("sender_name", "Server")!!,
            senderIcon = section.getString("sender_icon", "default")!!,
            title = section.getString("title", "")!!,
            body = section.getStringList("body"),
            ttlDays = section.getInt("ttl_days", 15),
            requiresOnline = section.getBoolean("requires_online", false),
            rewards = parseRewards(section.getConfigurationSection("rewards"))
        )
    }

    private fun parseRewards(section: ConfigurationSection?): ComplexMailRewards {
        if (section == null) return ComplexMailRewards(emptyList(), emptyList())

        val physical = section.getMapList("physical").mapNotNull { map ->
            val material = map["material"] as? String ?: return@mapNotNull null
            PhysicalRewardConfig(
                material = material,
                amount = (map["amount"] as? Int) ?: 1,
                name = map["name"] as? String
            )
        }

        val commands = section.getMapList("commands").mapNotNull { map ->
            val command = map["command"] as? String ?: return@mapNotNull null
            val displayName = map["display_name"] as? String ?: return@mapNotNull null
            val displayMaterial = map["display_material"] as? String ?: "PAPER"
            CommandRewardConfig(
                command = command,
                displayName = displayName,
                displayMaterial = displayMaterial
            )
        }

        return ComplexMailRewards(physical, commands)
    }

    fun getConfig(id: String): ComplexMailConfig? = configs[id]

    fun getAllIds(): Set<String> = configs.keys
}
