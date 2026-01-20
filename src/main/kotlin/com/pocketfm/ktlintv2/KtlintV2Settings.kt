package com.pocketfm.ktlintv2

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent settings for ktlintv2 plugin.
 */
@State(
    name = "KtlintV2Settings",
    storages = [Storage("ktlintv2.xml")]
)
@Service(Service.Level.PROJECT)
class KtlintV2Settings : PersistentStateComponent<KtlintV2Settings> {
    
    // Whether linting is enabled
    var lintEnabled: Boolean = true
    
    // Whether to format on save
    var formatOnSave: Boolean = false
    
    // Whether to use standard ktlint rules
    var useStandardRules: Boolean = true
    
    // Path to custom standard rules JAR (empty = use bundled)
    var customStandardRulesJarPath: String = ""
    
    // Paths to additional rule JARs (one per line)
    var additionalRuleJarPaths: String = ""
    
    // Disabled rule IDs (comma-separated)
    // Note: Some standard rules have false positives or conflict with common code patterns
    var disabledRules: String = "standard:class-signature,standard:wrapping"
    
    // Rules to treat as warnings instead of errors (comma-separated)
    var warningRules: String = ""
    
    // EditorConfig path (empty = auto-detect)
    var editorConfigPath: String = ""
    
    // Whether to treat all errors as warnings
    var treatAllErrorsAsWarnings: Boolean = false
    
    // Whether to show inline annotations
    var showInlineAnnotations: Boolean = true

    override fun getState(): KtlintV2Settings {
        return this
    }

    override fun loadState(state: KtlintV2Settings) {
        XmlSerializerUtil.copyBean(state, this)
    }
    
    // Helper to get disabled rules as Set
    fun getDisabledRuleSet(): Set<String> {
        return disabledRules.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
    
    // Helper to get warning rules as Set
    fun isWarningRule(ruleId: String): Boolean {
        if (treatAllErrorsAsWarnings) return true
        return warningRules.split(",")
            .map { it.trim() }
            .contains(ruleId)
    }

    // Helper for additional JAR paths list
    fun getAdditionalRuleJarPathsList(): List<String> {
        return additionalRuleJarPaths.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
    
    fun setAdditionalRuleJarPathsList(paths: List<String>) {
        additionalRuleJarPaths = paths.joinToString("\n")
    }
    
    companion object {
        fun getInstance(project: Project): KtlintV2Settings {
            return project.getService(KtlintV2Settings::class.java)
        }
    }
}
