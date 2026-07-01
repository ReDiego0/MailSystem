package org.ReDiego0.mailSystem.api

import org.ReDiego0.mailSystem.manager.SimpleMailManager
import org.ReDiego0.mailSystem.model.Mail
import org.ReDiego0.mailSystem.model.MailProfile
import java.util.UUID
import java.util.concurrent.CompletableFuture

class SimpleMailApi(
    private val manager: SimpleMailManager
) : MailApi {

    override fun sendMail(recipientUUID: UUID, builder: MailBuilder.() -> Unit): CompletableFuture<Boolean> {
        val mail = MailBuilder().apply(builder).build(recipientUUID)
        return sendMail(recipientUUID, mail)
    }

    override fun sendMail(recipientUUID: UUID, mail: Mail): CompletableFuture<Boolean> =
        manager.deliverMail(mail).thenApply { true }

    override fun getMailProfile(playerUUID: UUID): CompletableFuture<MailProfile?> =
        manager.loadProfile(playerUUID).thenApply { it }

    override fun getMail(mailId: UUID): CompletableFuture<Mail?> =
        manager.getStorage().getMail(mailId)

    override fun hasUnreadMail(playerUUID: UUID): CompletableFuture<Boolean> =
        manager.getStorage().hasUnreadMail(playerUUID)
}
