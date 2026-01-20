package com.pocketfm.ktlintv2.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.pocketfm.ktlintv2.engine.KtlintEngineWrapper

/**
 * Action to format the current Kotlin file using ktlint.
 */
class FormatAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val document = editor.document
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return

        // Only process Kotlin files
        if (!file.name.endsWith(".kt") && !file.name.endsWith(".kts")) {
            return
        }

        val originalCode = document.text
        val filePath = file.virtualFile?.path ?: file.name

        val engine = KtlintEngineWrapper.getInstance(project)
        val formattedCode = engine.format(originalCode, filePath)

        if (formattedCode != originalCode) {
            WriteCommandAction.runWriteCommandAction(project, COMMAND, null, {
                document.setText(formattedCode)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            })
        }
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE)
        val isKotlinFile = file?.name?.endsWith(".kt") == true ||
                file?.name?.endsWith(".kts") == true
        e.presentation.isEnabledAndVisible = isKotlinFile
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private companion object {
        const val COMMAND = "Format with ktlint"
    }
}


