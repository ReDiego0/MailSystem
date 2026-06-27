package org.ReDiego0.mailSystem.model

import java.util.UUID

data class MailProfile(
    val playerUUID: UUID,
    val playerName: String,
    val mails: MutableList<Mail>,
    val maxCapacity: Int = 50,
    val lastAccessed: Long
)
