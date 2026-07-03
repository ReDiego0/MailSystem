package org.ReDiego0.mailSystem.maintenance

import org.ReDiego0.mailSystem.manager.MailManager
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable

class ExpirationTask(
    private val plugin: JavaPlugin,
    private val manager: MailManager,
    private val intervalHours: Long
) : BukkitRunnable() {

    override fun run() {
        manager.expireOldMails().thenAccept { count ->
            if (count > 0) {
                plugin.logger.info("Expired $count mail(s)")
            }
        }
    }

    fun start() {
        val ticks = intervalHours * 60 * 60 * 20
        runTaskTimerAsynchronously(plugin, ticks, ticks)
        plugin.logger.info("Expiration task started (every ${intervalHours}h)")
    }
}
