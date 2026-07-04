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
            plugin.dataFolder.mkdirs()
            generateDefault(file)
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
        val config = ComplexMailConfig(
            id = id,
            senderName = section.getString("sender_name", "Server")!!,
            senderIcon = section.getString("sender_icon", "default")!!,
            title = section.getString("title", "")!!,
            body = section.getStringList("body"),
            ttlDays = section.getInt("ttl_days", 15),
            requiresOnline = section.getBoolean("requires_online", false),
            rewards = parseRewards(section.getConfigurationSection("rewards")),
            conditions = parseConditions(section.getConfigurationSection("conditions"))
        )

        if (config.conditions != null && config.conditions.papi.isNotEmpty() && !config.requiresOnline) {
            plugin.logger.warning("Complex mail '$id' has PAPI conditions but requires_online is false. Forcing requires_online = true.")
        }

        return config
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

    private fun parseConditions(section: ConfigurationSection?): ComplexMailConditions? {
        if (section == null) return null

        val logic = section.getString("logic", "and") ?: "and"
        val papi = section.getMapList("papi").mapNotNull { map ->
            val placeholder = map["placeholder"] as? String ?: return@mapNotNull null
            val operator = map["operator"] as? String ?: return@mapNotNull null
            val value = map["value"] as? String ?: return@mapNotNull null
            PapiConditionConfig(placeholder, operator, value)
        }
        val permissions = section.getStringList("permissions")

        if (papi.isEmpty() && permissions.isEmpty()) return null

        return ComplexMailConditions(logic, papi, permissions)
    }

    private fun generateDefault(file: File) {
        val yaml = YamlConfiguration()

        yaml.set("complex_mails.welcome.sender_name", "Welcome Committee")
        yaml.set("complex_mails.welcome.sender_icon", "default")
        yaml.set("complex_mails.welcome.title", "Welcome to the server!")
        yaml.set("complex_mails.welcome.body", listOf("Welcome to our server!", "Enjoy your stay."))
        yaml.set("complex_mails.welcome.ttl_days", 30)
        yaml.set("complex_mails.welcome.requires_online", false)
        yaml.set("complex_mails.welcome.rewards.physical", listOf(
            mapOf("material" to "DIAMOND", "amount" to 5)
        ))
        yaml.set("complex_mails.welcome.rewards.commands", listOf(
            mapOf("command" to "give %player% iron_ingot 10", "display_name" to "&fIron Ingot ×10", "display_material" to "IRON_INGOT")
        ))

        yaml.set("complex_mails.maintenance.sender_name", "Server")
        yaml.set("complex_mails.maintenance.sender_icon", "BARRIER")
        yaml.set("complex_mails.maintenance.title", "Server Maintenance")
        yaml.set("complex_mails.maintenance.body", listOf("The server will undergo maintenance."))
        yaml.set("complex_mails.maintenance.ttl_days", 1)
        yaml.set("complex_mails.maintenance.requires_online", true)
        yaml.set("complex_mails.maintenance.rewards.physical", emptyList<Map<String, Any>>())
        yaml.set("complex_mails.maintenance.rewards.commands", emptyList<Map<String, Any>>())

        yaml.save(file)
        plugin.logger.info("Generated default complex_mails.yml")
    }

    fun getConfig(id: String): ComplexMailConfig? = configs[id]

    fun getAllIds(): Set<String> = configs.keys
}
