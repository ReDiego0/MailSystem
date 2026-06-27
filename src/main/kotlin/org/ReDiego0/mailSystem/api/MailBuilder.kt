package org.ReDiego0.mailSystem.api

import org.ReDiego0.mailSystem.model.Mail
import org.ReDiego0.mailSystem.model.MailSource
import org.ReDiego0.mailSystem.model.MailStatus
import org.ReDiego0.mailSystem.reward.CommandReward
import org.ReDiego0.mailSystem.reward.PhysicalReward
import org.ReDiego0.mailSystem.reward.Reward
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import com.destroystokyo.paper.profile.ProfileProperty
import java.util.Base64
import java.util.UUID

class MailBuilder {
    private var senderName: String = "Server"
    private var senderIcon: ItemStack = createDefaultSenderIcon()
    private var title: String = ""
    private var body: MutableList<String> = mutableListOf()
    private var rewards: MutableList<Reward> = mutableListOf()
    private var ttlDays: Int = 15
    private var source: MailSource = MailSource.Api

    fun sender(name: String, icon: ItemStack): MailBuilder {
        this.senderName = name
        this.senderIcon = icon
        return this
    }

    fun title(title: String): MailBuilder {
        this.title = title
        return this
    }

    fun bodyLine(line: String): MailBuilder {
        this.body.add(line)
        return this
    }

    fun body(lines: List<String>): MailBuilder {
        this.body.addAll(lines)
        return this
    }

    fun addPhysicalReward(item: ItemStack): MailBuilder {
        this.rewards.add(PhysicalReward(item))
        return this
    }

    fun addCommandReward(displayItem: ItemStack, commands: List<String>): MailBuilder {
        this.rewards.add(CommandReward(displayItem, commands))
        return this
    }

    fun ttl(days: Int): MailBuilder {
        this.ttlDays = days
        return this
    }

    fun source(source: MailSource): MailBuilder {
        this.source = source
        return this
    }

    fun build(recipientUUID: UUID): Mail {
        val now = System.currentTimeMillis()
        val expiresAt = now + (ttlDays.toLong() * 24 * 60 * 60 * 1000)
        return Mail(
            id = UUID.randomUUID(),
            recipientUUID = recipientUUID,
            senderName = senderName,
            senderIcon = senderIcon,
            title = title,
            body = body.toList(),
            rewards = rewards.toList(),
            createdAt = now,
            expiresAt = expiresAt,
            status = MailStatus.UNREAD,
            source = source
        )
    }

    companion object {
        private val DEFAULT_TEXTURE_JSON = """{"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/6ca96736562382669a28b55b161229a0ddbd23032dbab75eb5c54be4d9e84e4"}}}"""
        private val DEFAULT_TEXTURE_B64: String = Base64.getEncoder().encodeToString(DEFAULT_TEXTURE_JSON.toByteArray())

        fun createDefaultSenderIcon(): ItemStack {
            val head = ItemStack(Material.PLAYER_HEAD)
            val meta = head.itemMeta as SkullMeta
            val profile = Bukkit.createProfile(UUID.randomUUID())
            profile.setProperty(ProfileProperty("textures", DEFAULT_TEXTURE_B64))
            meta.playerProfile = profile
            head.itemMeta = meta
            return head
        }
    }
}
