package org.ReDiego0.mailSystem.api

import org.ReDiego0.mailSystem.model.Mail
import org.ReDiego0.mailSystem.model.MailProfile
import java.util.UUID
import java.util.concurrent.CompletableFuture

interface MailApi {
    fun sendMail(recipientUUID: UUID, builder: MailBuilder.() -> Unit): CompletableFuture<Boolean>
    fun sendMail(recipientUUID: UUID, mail: Mail): CompletableFuture<Boolean>
    fun getMailProfile(playerUUID: UUID): CompletableFuture<MailProfile?>
    fun getMail(mailId: UUID): CompletableFuture<Mail?>
    fun hasUnreadMail(playerUUID: UUID): CompletableFuture<Boolean>
}
