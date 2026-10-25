package org.ReDiego0.mailSystem.api

import org.ReDiego0.mailSystem.model.Mail
import org.ReDiego0.mailSystem.reward.PhysicalReward
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Se dispara antes de entregar las recompensas físicas de un correo.
 *
 * Los listeners pueden retirar recompensas de [physicalRewards] (por ejemplo,
 * para evitar núcleos duplicados). Las recompensas de comando no se modifican
 * desde este evento.
 */
class MailClaimEvent(
    val player: Player,
    val mail: Mail,
    val physicalRewards: MutableList<PhysicalReward>
) : Event() {

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
