package org.ReDiego0.mailSystem.manager

import org.ReDiego0.mailSystem.api.MailApi
import org.ReDiego0.mailSystem.model.Mail
import org.ReDiego0.mailSystem.model.MailProfile
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.CompletableFuture

sealed interface ClaimResult {
    data object Success : ClaimResult
    data object InventoryFull : ClaimResult
    data object AlreadyClaimed : ClaimResult
    data object MailNotFound : ClaimResult
    data object Expired : ClaimResult
    data object NoRewards : ClaimResult
    data class CommandFailed(val command: String, val error: String) : ClaimResult
}

interface MailManager {
    fun deliverMail(mail: Mail): CompletableFuture<Void>
    fun claimRewards(mailId: UUID, player: Player): CompletableFuture<ClaimResult>
    fun deleteMail(mailId: UUID, playerUUID: UUID): CompletableFuture<Boolean>
    fun deleteAllRead(playerUUID: UUID): CompletableFuture<Int>
    fun expireOldMails(): CompletableFuture<Int>
    fun loadProfile(playerUUID: UUID): CompletableFuture<MailProfile>
    fun saveProfile(profile: MailProfile): CompletableFuture<Void>
    fun getApi(): MailApi
}
