package org.ReDiego0.mailSystem.storage

import org.ReDiego0.mailSystem.model.Mail
import org.ReDiego0.mailSystem.model.MailProfile
import org.ReDiego0.mailSystem.model.MailStatus
import org.ReDiego0.mailSystem.reward.Reward
import java.util.UUID
import java.util.concurrent.CompletableFuture

interface MailStorage {
    fun initialize(): CompletableFuture<Void>
    fun shutdown(): CompletableFuture<Void>

    fun loadProfile(playerUUID: UUID): CompletableFuture<MailProfile?>
    fun saveProfile(profile: MailProfile): CompletableFuture<Void>

    fun saveMail(mail: Mail): CompletableFuture<Void>
    fun getMail(mailId: UUID): CompletableFuture<Mail?>
    fun getMails(playerUUID: UUID): CompletableFuture<List<Mail>>
    fun updateMailStatus(mailId: UUID, status: MailStatus): CompletableFuture<Boolean>
    fun deleteMail(mailId: UUID): CompletableFuture<Boolean>
    fun deleteAllRead(playerUUID: UUID): CompletableFuture<Int>
    fun expireOldMails(): CompletableFuture<Int>
    fun hasUnreadMail(playerUUID: UUID): CompletableFuture<Boolean>

    fun saveRewards(mailId: UUID, rewards: List<Reward>): CompletableFuture<Void>
    fun getRewards(mailId: UUID): CompletableFuture<List<Reward>>
    fun markRewardClaimed(rewardId: Int): CompletableFuture<Boolean>

    fun getAllProfileUUIDs(): CompletableFuture<List<UUID>>

    fun getMailCount(playerUUID: UUID): CompletableFuture<Int>
    fun getQueuedMails(playerUUID: UUID): CompletableFuture<List<Mail>>
    fun promoteOldestQueued(playerUUID: UUID): CompletableFuture<Boolean>
    fun getExpiredPlayerUUIDs(): CompletableFuture<List<UUID>>
}
