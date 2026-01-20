package com.pocketfm.ktlintv2

import com.intellij.openapi.project.Project
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pocketfm.ktlintv2.engine.KtlintEngineWrapper

/**
 * External annotator that runs ktlint on Kotlin files and shows results as annotations.
 */
class KtlintAnnotator : ExternalAnnotator<KtlintAnnotator.Input, KtlintAnnotator.Result>() {
    
    data class Input(
        val code: String,
        val filePath: String,
        val document: Document,
        val project: Project
    )
    
    data class Result(
        val errors: List<LintError>,
        val document: Document
    )
    
    override fun collectInformation(file: PsiFile): Input? {
        val project = file.project
        val settings = KtlintV2Settings.getInstance(project)
        if (!settings.lintEnabled) return null
        
        // Only process Kotlin files
        if (!file.name.endsWith(".kt") && !file.name.endsWith(".kts")) {
            return null
        }
        
        val document = file.viewProvider.document ?: return null
        val code = document.text
        val filePath = file.virtualFile?.path ?: file.name
        
        return Input(code, filePath, document, project)
    }
    
    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Input? {
        return collectInformation(file)
    }
    
    override fun doAnnotate(input: Input?): Result? {
        if (input == null) return null
        
        val engine = KtlintEngineWrapper.getInstance(input.project)
        val errors = engine.lint(input.code, input.filePath)
        
        return Result(errors, input.document)
    }
    
    override fun apply(file: PsiFile, result: Result?, holder: AnnotationHolder) {
        if (result == null) return
        
        val settings = KtlintV2Settings.getInstance(file.project)
        
        for (error in result.errors) {
            try {
                val textRange = getTextRange(result.document, error.line, error.col)
                if (textRange != null) {
                    val message = buildMessage(error)
                    
                    // Determine severity based on rule and settings
                    val severity = if (settings.isWarningRule(error.ruleId.value)) {
                        HighlightSeverity.WARNING
                    } else {
                        HighlightSeverity.ERROR
                    }
                    
                    holder.newAnnotation(severity, message)
                        .range(textRange)
                        .tooltip(buildTooltip(error))
                        .create()
                }
            } catch (_: Exception) {
                // Skip errors we can't display
            }
        }
    }
    
    /**
     * Convert line/column to text range.
     */
    private fun getTextRange(document: Document, line: Int, col: Int): TextRange? {
        if (line < 1 || line > document.lineCount) return null
        
        val lineStartOffset = document.getLineStartOffset(line - 1)
        val lineEndOffset = document.getLineEndOffset(line - 1)
        val lineText = document.getText(TextRange(lineStartOffset, lineEndOffset))
        
        // Find the token at the column
        val colOffset = (col - 1).coerceIn(0, lineText.length)
        val startOffset = lineStartOffset + colOffset
        
        // Highlight the word at the position, or use a small range
        var endOffset = startOffset
        while (endOffset < lineEndOffset && !document.getText(TextRange(endOffset, endOffset + 1)).matches(Regex("\\s"))) {
            endOffset++
        }
        
        if (endOffset == startOffset) {
            endOffset = minOf(startOffset + 1, lineEndOffset)
        }
        
        return TextRange(startOffset, endOffset)
    }
    
    /**
     * Build annotation message.
     */
    private fun buildMessage(error: LintError): String {
        return "${error.detail} (${error.ruleId.value})"
    }
    
    /**
     * Build tooltip with more details.
     */
    private fun buildTooltip(error: LintError): String {
        return """
            <html>
            <body>
            <b>ktlint:</b> ${error.detail}<br/>
            <b>Rule:</b> ${error.ruleId.value}<br/>
            <b>Location:</b> Line ${error.line}, Column ${error.col}
            ${if (error.canBeAutoCorrected) "<br/><i>Can be auto-corrected</i>" else ""}
            </body>
            </html>
        """.trimIndent()
    }
}
