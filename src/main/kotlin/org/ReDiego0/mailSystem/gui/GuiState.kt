package org.ReDiego0.mailSystem.gui

import org.ReDiego0.mailSystem.model.Mail
import java.util.UUID

data class GuiState(
    val playerUUID: UUID,
    var currentPage: Int = 0,
    var selectedMailId: UUID? = null,
    var mails: List<Mail> = emptyList()
) {
    val selectedMail: Mail? get() = mails.find { it.id == selectedMailId }

    val totalPages: Int get() = maxOf(1, (mails.size + GuiConstants.MAILS_PER_PAGE - 1) / GuiConstants.MAILS_PER_PAGE)

    val pageMails: List<Mail>
        get() {
            val start = currentPage * GuiConstants.MAILS_PER_PAGE
            return mails.drop(start).take(GuiConstants.MAILS_PER_PAGE)
        }

    fun selectMail(mailId: UUID) {
        selectedMailId = if (selectedMailId == mailId) null else mailId
    }
}
