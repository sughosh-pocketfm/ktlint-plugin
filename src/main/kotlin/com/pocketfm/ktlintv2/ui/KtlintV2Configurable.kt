package com.pocketfm.ktlintv2.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.pocketfm.ktlintv2.KtlintV2Bundle
import com.pocketfm.ktlintv2.KtlintV2Settings
import javax.swing.DefaultListModel
import javax.swing.JComponent

/**
 * Settings UI for ktlintv2 plugin.
 */
import com.intellij.openapi.project.Project

/**
 * Settings UI for ktlintv2 plugin.
 */
class KtlintV2Configurable(private val project: Project) : Configurable {
    
    private var panel: DialogPanel? = null
    private val settings = KtlintV2Settings.getInstance(project)
    
    // Rule JAR list model
    private val ruleJarListModel = DefaultListModel<String>()
    private lateinit var ruleJarList: JBList<String>
    
    // UI components
    private var lintEnabledCheckbox: JBCheckBox? = null
    private var formatOnSaveCheckbox: JBCheckBox? = null
    private var useStandardRulesCheckbox: JBCheckBox? = null
    private var treatAllAsWarningsCheckbox: JBCheckBox? = null
    private var showInlineCheckbox: JBCheckBox? = null
    private var disabledRulesField: JBTextField? = null
    private var warningRulesField: JBTextField? = null
    private var customStandardRulesField: JBTextField? = null
    private var editorConfigField: JBTextField? = null
    
    override fun getDisplayName(): String = KtlintV2Bundle.message("settings.displayName")
    
    override fun createComponent(): JComponent {
        // Initialize rule JAR list
        ruleJarList = JBList(ruleJarListModel)
        loadRuleJarPaths()
        
        // Create toolbar decorator with + and - buttons
        val ruleJarPanel = ToolbarDecorator.createDecorator(ruleJarList)
            .setAddAction { addRuleJar() }
            .setRemoveAction { removeSelectedRuleJar() }
            .disableUpDownActions()
            .createPanel()
        
        panel = panel {
            group(KtlintV2Bundle.message("settings.linting.group")) {
                row {
                    lintEnabledCheckbox = checkBox(KtlintV2Bundle.message("settings.linting.enabled"))
                        .bindSelected(settings::lintEnabled)
                        .component
                }
                row {
                    formatOnSaveCheckbox = checkBox(KtlintV2Bundle.message("settings.linting.formatOnSave"))
                        .bindSelected(settings::formatOnSave)
                        .component
                }
                row {
                    showInlineCheckbox = checkBox(KtlintV2Bundle.message("settings.linting.showInlineAnnotations"))
                        .bindSelected(settings::showInlineAnnotations)
                        .component
                }
            }
            
            group(KtlintV2Bundle.message("settings.severity.group")) {
                row {
                    treatAllAsWarningsCheckbox = checkBox(KtlintV2Bundle.message("settings.linting.treatAllAsWarnings"))
                        .bindSelected(settings::treatAllErrorsAsWarnings)
                        .component
                }
                row {
                    label("Rules to treat as warnings (comma-separated):")
                }
                row {
                    warningRulesField = textField()
                        .bindText(settings::warningRules)
                        .columns(COLUMNS_LARGE)
                        .comment("Example: standard:no-wildcard-imports,pocketfm:magic-number")
                        .component
                }
                row {
                    comment(KtlintV2Bundle.message("settings.note"))
                }
            }
            
            group(KtlintV2Bundle.message("settings.rules.group")) {
                row {
                    useStandardRulesCheckbox = checkBox(KtlintV2Bundle.message("settings.rules.useStandard"))
                        .bindSelected(settings::useStandardRules)
                        .component
                }
                
                // Standard Rules Source Selector
                row("Standard Rules Version:") {
                    val sourceModel = javax.swing.DefaultComboBoxModel(arrayOf(
                        KtlintV2Bundle.message("settings.rules.bundled") + " (1.5.0)", 
                        KtlintV2Bundle.message("settings.rules.custom")
                    ))
                    val comboBox = comboBox(sourceModel)
                        .bindItem(
                            { if (settings.customStandardRulesJarPath.isBlank()) 
                                KtlintV2Bundle.message("settings.rules.bundled") + " (1.5.0)" 
                              else 
                                KtlintV2Bundle.message("settings.rules.custom") },
                            { /* We handle save manually or via the text field logic below */ }
                        ).component
                        
                    // Logic to enable/disable field based on selection
                    comboBox.addActionListener {
                        val isCustom = comboBox.selectedItem == KtlintV2Bundle.message("settings.rules.custom")
                        customStandardRulesField?.isEnabled = isCustom
                        if (!isCustom) {
                            customStandardRulesField?.text = ""
                        }
                    }
                }
                
                row("Custom Rules JAR:") {
                    customStandardRulesField = textField()
                        .bindText(settings::customStandardRulesJarPath)
                        .columns(COLUMNS_LARGE)
                        .enabled(settings.customStandardRulesJarPath.isNotBlank()) // Initial state
                        .component
                        
                    button(KtlintV2Bundle.message("settings.editorconfig.browse")) {
                        browseForCustomStandardRules()
                    }
                }
                row {
                    comment("Select 'Custom JAR' above to use a specific version of ktlint-ruleset-standard")
                }

                row {
                    label("Disabled rules (comma-separated):")
                }
                row {
                    disabledRulesField = textField()
                        .bindText(settings::disabledRules)
                        .columns(COLUMNS_LARGE)
                        .comment("These rules will be completely skipped")
                        .component
                }
            }
            
            group(KtlintV2Bundle.message("settings.projectRules.group")) {
                row {
                    label(KtlintV2Bundle.message("settings.projectRules.addHint"))
                }
                row {
                    cell(ruleJarPanel)
                        .align(AlignX.FILL)
                        .resizableColumn()
                }
                row {
                    comment("Add paths to custom ktlint rule JARs (e.g., linter.jar, compose-rules.jar)")
                }
            }
            
            group(KtlintV2Bundle.message("settings.editorconfig.group")) {
                row(KtlintV2Bundle.message("settings.editorconfig.path")) {
                    editorConfigField = textField()
                        .bindText(settings::editorConfigPath)
                        .columns(COLUMNS_LARGE)
                        .component
                    button(KtlintV2Bundle.message("settings.editorconfig.browse")) {
                        browseForEditorConfig()
                    }
                }
                row {
                    comment("Specify path to .editorconfig file for rule configuration")
                }
            }
        }
        
        return panel!!
    }
    
    private fun loadRuleJarPaths() {
        ruleJarListModel.clear()
        settings.getAdditionalRuleJarPathsList().forEach { path ->
            ruleJarListModel.addElement(path)
        }
    }
    
    private fun addRuleJar() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("jar")
            .withTitle("Select Rule JAR")
            .withDescription("Select a ktlint rule JAR file")
        
        com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, project, null) { file ->
            val path = file.path
            if (!ruleJarListModel.contains(path)) {
                ruleJarListModel.addElement(path)
            }
        }
    }
    
    private fun removeSelectedRuleJar() {
        val selectedIndex = ruleJarList.selectedIndex
        if (selectedIndex >= 0) {
            ruleJarListModel.remove(selectedIndex)
        }
    }
    
    private fun saveRuleJarPaths() {
        val paths = mutableListOf<String>()
        for (i in 0 until ruleJarListModel.size()) {
            paths.add(ruleJarListModel.getElementAt(i))
        }
        settings.setAdditionalRuleJarPathsList(paths)
    }
    
    override fun isModified(): Boolean {
        // Check if rule JAR list changed
        val currentPaths = settings.getAdditionalRuleJarPathsList()
        val listPaths = mutableListOf<String>()
        for (i in 0 until ruleJarListModel.size()) {
            listPaths.add(ruleJarListModel.getElementAt(i))
        }
        if (currentPaths != listPaths) return true
        
        return panel?.isModified() ?: false
    }
    
    override fun apply() {
        panel?.apply()
        saveRuleJarPaths()
    }
    
    override fun reset() {
        panel?.reset()
        loadRuleJarPaths()
    }
    
    private fun browseForEditorConfig() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("editorconfig")
            .withTitle("Select .editorconfig")
            .withDescription("Select the .editorconfig file to use for linting")
            
        com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, project, null) { file ->
            editorConfigField?.text = file.path
        }
    }

    private fun browseForCustomStandardRules() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("jar")
            .withTitle("Select Standard Rules JAR")
            .withDescription("Select a ktlint-ruleset-standard JAR file")
            
        com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, project, null) { file ->
            customStandardRulesField?.text = file.path
        }
    }

    override fun disposeUIResources() {
        panel = null
    }
}
