package com.pocketfm.ktlintv2.listeners

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.application.ApplicationManager

/**
 * Listener to watch for .editorconfig changes and trigger re-linting.
 */
class KtlintConfigListener(private val project: Project) : BulkFileListener {

    override fun after(events: MutableList<out VFileEvent>) {
        var configChanged = false
        
        for (event in events) {
            val file = event.file
            if (file != null && file.name == ".editorconfig") {
                configChanged = true
                break
            }
        }
        
        if (configChanged) {
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                
                // Reset ktlint engine to reload configuration
                com.pocketfm.ktlintv2.engine.KtlintEngineWrapper.getInstance(project).reset()
                
                // Restart daemon to force re-analysis of open files
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        }
    }
}
