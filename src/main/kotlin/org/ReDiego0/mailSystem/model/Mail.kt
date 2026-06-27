package org.ReDiego0.mailSystem.model

import org.ReDiego0.mailSystem.reward.Reward
import org.bukkit.inventory.ItemStack
import java.util.UUID

sealed interface MailSource {
    data object Api : MailSource
    data object Config : MailSource
    data class Complex(val templateId: String) : MailSource
    data object System : MailSource

    fun toStorageString(): String = when (this) {
        is Api -> "api"
        is Config -> "config"
        is Complex -> "complex:$templateId"
        is System -> "system"
    }

    companion object {
        fun fromStorageString(value: String): MailSource = when {
            value == "api" -> Api
            value == "config" -> Config
            value.startsWith("complex:") -> Complex(value.removePrefix("complex:"))
            value == "system" -> System
            else -> Api
        }
    }
}

data class Mail(
    val id: UUID,
    val recipientUUID: UUID,
    val senderName: String,
    val senderIcon: ItemStack,
    val title: String,
    val body: List<String>,
    val rewards: List<Reward>,
    val createdAt: Long,
    val expiresAt: Long,
    val status: MailStatus,
    val source: MailSource
)
