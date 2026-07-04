package org.ReDiego0.mailSystem.logging

import org.ReDiego0.mailSystem.reward.CommandReward
import org.ReDiego0.mailSystem.reward.PhysicalReward
import org.ReDiego0.mailSystem.reward.Reward
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

class AuditLogger(plugin: JavaPlugin) {

    private val logFile: File

    init {
        val dir = File(plugin.dataFolder, "logs")
        dir.mkdirs()
        logFile = File(dir, "audit.log")
    }

    fun logClaim(playerName: String, mailId: UUID, rewards: List<Reward>) {
        val descriptions = rewards.map { reward ->
            when (reward) {
                is PhysicalReward -> "${reward.item.type}×${reward.item.amount}"
                is CommandReward -> "cmd:${reward.commands.joinToString(",")}"
            }
        }
        log("[CLAIM] player=$playerName mailId=$mailId rewards=[${descriptions.joinToString("; ")}]")
    }

    fun logPurge(adminName: String, targetPlayer: String, mailCount: Int) {
        log("[PURGE] admin=$adminName target=$targetPlayer mailsDeleted=$mailCount")
    }

    private fun log(message: String) {
        val timestamp = DATE_FORMAT.format(Date())
        logFile.appendText("[$timestamp] $message\n")
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    }
}
