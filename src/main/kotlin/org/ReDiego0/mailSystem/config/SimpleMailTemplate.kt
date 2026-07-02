package org.ReDiego0.mailSystem.config

import org.bukkit.Material
import org.bukkit.configuration.Configuration
import org.bukkit.inventory.ItemStack
import org.ReDiego0.mailSystem.api.MailBuilder

data class SimpleMailTemplate(
    val senderName: String,
    val senderIcon: String,
    val ttlDays: Int
) {
    fun createSenderIcon(): ItemStack {
        if (senderIcon.equals("default", ignoreCase = true)) {
            return MailBuilder.createDefaultSenderIcon()
        }
        return try {
            ItemStack(Material.valueOf(senderIcon.uppercase()))
        } catch (_: IllegalArgumentException) {
            MailBuilder.createDefaultSenderIcon()
        }
    }

    companion object {
        fun fromConfig(config: Configuration): SimpleMailTemplate = SimpleMailTemplate(
            senderName = config.getString("simple_mail.sender_name", "Server")!!,
            senderIcon = config.getString("simple_mail.sender_icon", "default")!!,
            ttlDays = config.getInt("simple_mail.ttl_days", 15)
        )
    }
}
