package org.ReDiego0.mailSystem.config

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.ReDiego0.mailSystem.api.MailBuilder
import org.ReDiego0.mailSystem.model.Mail
import org.ReDiego0.mailSystem.model.MailSource
import org.ReDiego0.mailSystem.reward.CommandReward
import org.ReDiego0.mailSystem.reward.PhysicalReward
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.UUID

data class ComplexMailConfig(
    val id: String,
    val senderName: String,
    val senderIcon: String,
    val title: String,
    val body: List<String>,
    val ttlDays: Int,
    val requiresOnline: Boolean,
    val rewards: ComplexMailRewards,
    val conditions: ComplexMailConditions?
) {
    val effectiveRequiresOnline: Boolean
        get() {
            if (conditions != null && conditions.papi.isNotEmpty()) {
                return true
            }
            return requiresOnline
        }
    fun buildMail(recipientUUID: UUID): Mail {
        val builder = MailBuilder()
            .sender(senderName, createSenderIcon())
            .title(title)
            .body(body)
            .ttl(ttlDays)
            .source(MailSource.Complex(id))

        for (physical in rewards.physical) {
            val item = buildPhysicalItem(physical)
            builder.addPhysicalReward(item)
        }

        for (command in rewards.commands) {
            val displayItem = buildCommandDisplayItem(command)
            builder.addCommandReward(displayItem, listOf(command.command))
        }

        return builder.build(recipientUUID)
    }

    companion object {
        private val LEGACY = LegacyComponentSerializer.legacyAmpersand()
    }

    private fun createSenderIcon(): ItemStack {
        if (senderIcon.equals("default", ignoreCase = true)) {
            return MailBuilder.createDefaultSenderIcon()
        }
        return try {
            ItemStack(Material.valueOf(senderIcon.uppercase()))
        } catch (_: IllegalArgumentException) {
            MailBuilder.createDefaultSenderIcon()
        }
    }

    private fun buildPhysicalItem(config: PhysicalRewardConfig): ItemStack {
        val material = try {
            Material.valueOf(config.material.uppercase())
        } catch (_: IllegalArgumentException) {
            Material.STONE
        }
        val item = ItemStack(material, config.amount)
        if (config.name != null) {
            val meta = item.itemMeta
            meta?.displayName(LEGACY.deserialize(config.name))
            item.itemMeta = meta
        }
        return item
    }

    private fun buildCommandDisplayItem(config: CommandRewardConfig): ItemStack {
        val material = try {
            Material.valueOf(config.displayMaterial.uppercase())
        } catch (_: IllegalArgumentException) {
            Material.PAPER
        }
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta?.displayName(LEGACY.deserialize(config.displayName))
        item.itemMeta = meta
        return item
    }
}

data class ComplexMailRewards(
    val physical: List<PhysicalRewardConfig>,
    val commands: List<CommandRewardConfig>
)

data class PhysicalRewardConfig(
    val material: String,
    val amount: Int,
    val name: String?
)

data class CommandRewardConfig(
    val command: String,
    val displayName: String,
    val displayMaterial: String
)

data class ComplexMailConditions(
    val logic: String,
    val papi: List<PapiConditionConfig>,
    val permissions: List<String>
) {
    fun hasConditions(): Boolean = papi.isNotEmpty() || permissions.isNotEmpty()
}

data class PapiConditionConfig(
    val placeholder: String,
    val operator: String,
    val value: String
)
