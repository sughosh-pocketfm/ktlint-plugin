package com.pocketfm.ktlintv2.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.pocketfm.ktlintv2.KtlintV2Bundle
import com.pocketfm.ktlintv2.engine.KtlintEngineWrapper

/**
 * Action to lint the current Kotlin file and show results in notification.
 */
class LintAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return

        // Only process Kotlin files
        if (!file.name.endsWith(".kt") && !file.name.endsWith(".kts")) {
            return
        }

        val filePath = file.virtualFile?.path ?: file.name

        val engine = KtlintEngineWrapper.getInstance(project)
        val errors = engine.lint(file.text, filePath)

        // Show notification with results
        val notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup("ktlintv2")

        val message = if (errors.isEmpty()) {
            KtlintV2Bundle.message("notification.lint.noErrors")
        } else {
            val errorDetails = errors.take(MAX_ERRORS_TO_SHOW)
                .joinToString("\n") { "  Line ${it.line}: ${it.detail} (${it.ruleId.value})" }
            val more = if (errors.size > MAX_ERRORS_TO_SHOW) "\n  ... and ${errors.size - MAX_ERRORS_TO_SHOW} more" else ""
            
            KtlintV2Bundle.message("notification.lint.errorsFound", errors.size) + ":\n" + errorDetails + more
        }

        val type = if (errors.isEmpty()) NotificationType.INFORMATION else NotificationType.WARNING

        notificationGroup
            .createNotification(KtlintV2Bundle.message("notification.title"), message, type)
            .notify(project)
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE)
        val isKotlinFile = file?.name?.endsWith(".kt") == true ||
                file?.name?.endsWith(".kts") == true
        e.presentation.isEnabledAndVisible = isKotlinFile
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private companion object {
        const val MAX_ERRORS_TO_SHOW = 5
    }
}
