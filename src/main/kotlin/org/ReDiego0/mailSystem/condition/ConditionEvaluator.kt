package org.ReDiego0.mailSystem.condition

import me.clip.placeholderapi.PlaceholderAPI
import net.luckperms.api.LuckPermsProvider
import org.ReDiego0.mailSystem.config.ComplexMailConditions
import org.ReDiego0.mailSystem.config.PapiConditionConfig
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class ConditionEvaluator(private val plugin: JavaPlugin) {

    private val papiAvailable: Boolean by lazy {
        Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
    }

    private val luckPermsAvailable: Boolean by lazy {
        Bukkit.getPluginManager().isPluginEnabled("LuckPerms")
    }

    fun evaluate(uuid: UUID, conditions: ComplexMailConditions): Boolean {
        if (!conditions.hasConditions()) return true

        val player = Bukkit.getPlayer(uuid)
        val isOnline = player != null && player.isOnline

        val results = mutableListOf<Boolean>()

        if (conditions.papi.isNotEmpty()) {
            if (!isOnline) {
                plugin.logger.warning("Cannot evaluate PAPI conditions for offline player $uuid, skipping PAPI")
                results.add(false)
            } else if (!papiAvailable) {
                plugin.logger.warning("PAPI conditions defined but PlaceholderAPI not available")
                results.add(false)
            } else {
                results.add(evaluatePapiConditions(player!!, conditions.papi))
            }
        }

        if (conditions.permissions.isNotEmpty()) {
            if (isOnline) {
                results.add(evaluateBukkitPermissions(player!!, conditions.permissions))
            } else if (luckPermsAvailable) {
                results.add(evaluateLuckPermsPermissions(uuid, conditions.permissions))
            } else {
                plugin.logger.warning("Cannot evaluate permissions for offline player $uuid without LuckPerms")
                results.add(false)
            }
        }

        return when (conditions.logic.lowercase()) {
            "or" -> results.any { it }
            else -> results.all { it }
        }
    }

    private fun evaluatePapiConditions(player: Player, conditions: List<PapiConditionConfig>): Boolean {
        for (condition in conditions) {
            val result = PlaceholderAPI.setPlaceholders(player, condition.placeholder)
            if (!evaluateComparison(result, condition.operator, condition.value)) {
                return false
            }
        }
        return true
    }

    private fun evaluateBukkitPermissions(player: Player, permissions: List<String>): Boolean {
        for (permission in permissions) {
            if (!player.hasPermission(permission)) return false
        }
        return true
    }

    private fun evaluateLuckPermsPermissions(uuid: UUID, permissions: List<String>): Boolean {
        try {
            val api = LuckPermsProvider.get()
            val userManager = api.userManager
            val user = userManager.getUser(uuid) ?: run {
                userManager.loadUser(uuid).join()
            }
            val permissionData = user.cachedData.permissionData
            for (permission in permissions) {
                if (!permissionData.checkPermission(permission).asBoolean()) return false
            }
            return true
        } catch (e: Exception) {
            plugin.logger.warning("Failed to evaluate LuckPerms permissions for $uuid: ${e.message}")
            return false
        }
    }

    private fun evaluateComparison(actual: String, operator: String, expected: String): Boolean {
        return when (operator) {
            "==" -> actual.equals(expected, ignoreCase = true)
            "!=" -> !actual.equals(expected, ignoreCase = true)
            ">=", "<=", ">", "<" -> {
                val actualNum = actual.toDoubleOrNull()
                val expectedNum = expected.toDoubleOrNull()
                if (actualNum == null || expectedNum == null) return false
                when (operator) {
                    ">=" -> actualNum >= expectedNum
                    "<=" -> actualNum <= expectedNum
                    ">" -> actualNum > expectedNum
                    "<" -> actualNum < expectedNum
                    else -> false
                }
            }
            else -> {
                plugin.logger.warning("Unknown operator: $operator")
                false
            }
        }
    }
}
