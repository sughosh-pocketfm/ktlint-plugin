package com.pocketfm.ktlintv2.engine

/**
 * Wrapper around KtLint engine with isolated class loading for rule JARs.
 */
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.EditorConfigDefaults
import com.pinterest.ktlint.rule.engine.api.EditorConfigOverride
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.ruleset.standard.StandardRuleSetProvider
import com.pocketfm.ktlintv2.KtlintV2Settings
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader

/**
 * Wrapper around KtLint engine with isolated class loading for rule JARs.
 */
@Service(Service.Level.PROJECT)
class KtlintEngineWrapper(private val project: Project) {

    private var engine: KtLintRuleEngine? = null
    private var lastConfigHash: Int = 0

    /**
     * Reset the engine to force reloading configuration.
     */
    @Synchronized
    fun reset() {
        engine = null
        lastConfigHash = 0
    }

    /**
     * Lint a Kotlin file and return list of errors.
     */
    fun lint(code: String, filePath: String): List<LintError> {
        val engine = getOrCreateEngine()
        val errors = mutableListOf<LintError>()
        try {
            val file = File(filePath)
            // Use reflection to create Code object with content and path
            // Workaround for private constructor and missing factory method
            val codeToLint = createCodeViaReflection(code, filePath.endsWith(".kts"), file.toPath())

            engine.lint(codeToLint) { error ->
                errors.add(error)
            }
        } catch (e: Exception) {
            // Log but don't crash
            println("ktlintv2: Error during lint: ${e.message}")
        }

        return errors
    }

    /**
     * Format a Kotlin file and return the formatted code.
     */
    fun format(
        code: String,
        filePath: String,
        isScript: Boolean = filePath.endsWith(".kts")
    ): String {
        val engine = getOrCreateEngine()

        return try {
            val file = File(filePath)
            val codeToFormat = createCodeViaReflection(code, isScript, file.toPath())
            engine.format(
                codeToFormat,
                rerunAfterAutocorrect = true,
                defaultAutocorrect = true
            ) { _ -> AutocorrectDecision.ALLOW_AUTOCORRECT }
        } catch (e: Exception) {
            println("ktlintv2: Error during format: ${e.message}")
            code // Return original on error
        }
    }

    /**
     * Helper to create Code instance via reflection since constructor is private
     * and fromSnippet doesn't support filePath in 1.5.0.
     */
    private fun createCodeViaReflection(
        content: String,
        isScript: Boolean,
        path: java.nio.file.Path
    ): Code {
        try {
            val constructor = Code::class.java.getDeclaredConstructor(
                String::class.java, // content
                String::class.java, // fileName
                java.nio.file.Path::class.java, // filePath
                Boolean::class.javaPrimitiveType, // script
                Boolean::class.javaPrimitiveType // isStdIn
            )
            constructor.isAccessible = true
            return constructor.newInstance(content, null, path, isScript, false)
        } catch (e: Exception) {
            // Fallback to fromSnippet if reflection fails (loses .editorconfig support)
            println("ktlintv2: Reflection creation failed, falling back to fromSnippet: ${e.message}")
            return Code.fromSnippet(content, isScript)
        }
    }

    /**
     * Get or create the KtLint engine, recreating if settings changed.
     */
    @Synchronized
    private fun getOrCreateEngine(): KtLintRuleEngine {
        val settings = KtlintV2Settings.getInstance(project)
        val configHash = computeConfigHash(settings)

        if (engine == null || configHash != lastConfigHash) {
            engine = createEngine(settings)
            lastConfigHash = configHash
        }

        return engine!!
    }

    /**
     * Create a new KtLint engine with current settings.
     */
    private fun createEngine(settings: KtlintV2Settings): KtLintRuleEngine {
        // ... (implementation remains same but uses settings passed in) ...
        return createEngineInternal(settings)
    }

    // Moved implementation to separate method to avoid indentation changes mess
    private fun createEngineInternal(settings: KtlintV2Settings): KtLintRuleEngine {
        val ruleProviders = mutableSetOf<RuleProvider>()
        val disabledRules = settings.getDisabledRuleSet()

        // Add standard rules if enabled
        if (settings.useStandardRules) {
            try {
                val customJarPath = settings.customStandardRulesJarPath
                if (customJarPath.isBlank()) {
                    // Use bundled standard rules
                    val standardProviders = StandardRuleSetProvider().getRuleProviders()
                    ruleProviders.addAll(filterRules(standardProviders, disabledRules))
                } else {
                    // Use custom standard rules JAR
                    val jarFile = File(customJarPath)
                    if (jarFile.exists()) {
                        val providers = loadRulesFromJar(jarFile)
                        ruleProviders.addAll(filterRules(providers, disabledRules))
                        println("ktlintv2: Loaded ${providers.size} standard rules from $customJarPath")
                    } else {
                        println("ktlintv2: Custom standard rules JAR not found: $customJarPath")
                        val standardProviders = StandardRuleSetProvider().getRuleProviders()
                        ruleProviders.addAll(filterRules(standardProviders, disabledRules))
                    }
                }
            } catch (e: Throwable) {
                println("ktlintv2: Error loading standard rules: ${e.message}")
            }
        }

        // Load additional rule JARs
        for (jarPath in settings.getAdditionalRuleJarPathsList()) {
            try {
                val jarFile = File(jarPath)
                if (jarFile.exists()) {
                    val providers = loadRulesFromJar(jarFile)
                    ruleProviders.addAll(filterRules(providers, disabledRules))
                    println("ktlintv2: Loaded ${providers.size} rules from $jarPath")
                } else {
                    println("ktlintv2: Rule JAR not found: $jarPath")
                }
            } catch (e: Throwable) {
                println("ktlintv2: Error loading rules from $jarPath: ${e.message}")
            }
        }

        // Get property types from all rule providers
        val propertyTypes = ruleProviders.flatMap { provider ->
            try {
                provider.createNewRuleInstance().usesEditorConfigProperties.map { it.type }
            } catch (_: Throwable) {
                emptyList()
            }
        }.toSet()

        // Try to find .editorconfig in common locations
        val editorConfigDefaults = try {
            val configPath = settings.editorConfigPath.takeIf { it.isNotBlank() }
            if (configPath != null && propertyTypes.isNotEmpty()) {
                val file = File(configPath)
                if (file.exists()) {
                    EditorConfigDefaults.load(file.toPath(), propertyTypes)
                } else {
                    EditorConfigDefaults.EMPTY_EDITOR_CONFIG_DEFAULTS
                }
            } else {
                EditorConfigDefaults.EMPTY_EDITOR_CONFIG_DEFAULTS
            }
        } catch (e: Throwable) {
            println("ktlintv2: Error loading editorconfig: ${e.message}")
            EditorConfigDefaults.EMPTY_EDITOR_CONFIG_DEFAULTS
        }

        return KtLintRuleEngine(
            ruleProviders = ruleProviders,
            editorConfigDefaults = editorConfigDefaults,
            editorConfigOverride = EditorConfigOverride.EMPTY_EDITOR_CONFIG_OVERRIDE
        )
    }

    /**
     * Load rule providers from a JAR file using isolated class loader.
     */
    private fun loadRulesFromJar(jarFile: File): Set<RuleProvider> {
        val classLoader = URLClassLoader(
            arrayOf(jarFile.toURI().toURL()),
            this::class.java.classLoader
        )

        val providers = mutableSetOf<RuleProvider>()

        try {
            val serviceLoader = ServiceLoader.load(
                RuleSetProviderV3::class.java,
                classLoader
            )

            for (provider in serviceLoader) {
                try {
                    providers.addAll(provider.getRuleProviders())
                } catch (e: Throwable) {
                    println("ktlintv2: Error loading rules from provider ${provider::class.java.name}: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            println("ktlintv2: Error loading service providers from $jarFile: ${e.message}")
        }

        return providers
    }

    /**
     * Filter out disabled rules.
     */
    private fun filterRules(
        providers: Set<RuleProvider>,
        disabledRules: Set<String>
    ): Set<RuleProvider> {
        if (disabledRules.isEmpty()) return providers

        return providers.filter { provider ->
            try {
                val ruleId = provider.createNewRuleInstance().ruleId.value
                !disabledRules.contains(ruleId)
            } catch (_: Throwable) {
                false // Skip problematic rules
            }
        }.toSet()
    }

    /**
     * Compute hash of settings to detect changes.
     */
    private fun computeConfigHash(settings: KtlintV2Settings): Int {
        var hash = listOf(
            settings.useStandardRules,
            settings.customStandardRulesJarPath,
            settings.additionalRuleJarPaths,
            settings.disabledRules,
            settings.editorConfigPath
        ).hashCode()

        // Include timestamp of .editorconfig if specified
        if (settings.editorConfigPath.isNotBlank()) {
            val file = File(settings.editorConfigPath)
            if (file.exists()) {
                hash = 31 * hash + file.lastModified().hashCode()
            }
        }

        return hash
    }

    companion object {
        fun getInstance(project: Project): KtlintEngineWrapper {
            return project.getService(KtlintEngineWrapper::class.java)
        }
    }
}
