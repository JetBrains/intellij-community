// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.framework.ui

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Pair
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurator
import org.jetbrains.kotlin.idea.configuration.getCanBeConfiguredModules
import org.jetbrains.kotlin.idea.configuration.getCanBeConfiguredModulesWithKotlinFiles
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle.message
import javax.swing.event.HyperlinkListener

private enum class ChoseModuleType {
    ALL, CONTAINING_KT, SINGLE
}

class ChooseModulePanel(
    private val project: Project,
    private val configurator: KotlinProjectConfigurator,
    // Currently unused
    excludedModule: Collection<Module>
) {
    internal val modules: List<Module>
    private val modulesWithKtFiles: List<Module>

    private val selectedModule: AtomicProperty<Module?>
    private val chosenModuleType: AtomicProperty<ChoseModuleType>

    init {
        val modulesPair = ActionUtil.underModalProgress<Pair<List<Module>, List<Module>>>(
            project,
            message("lookup.kotlin.modules.configurations.progress.text"),
            Computable {
                val modules = getCanBeConfiguredModules(project, configurator)
                val modulesWithKtFiles =
                    getCanBeConfiguredModulesWithKotlinFiles(project, configurator)
                Pair.create(
                    modules,
                    modulesWithKtFiles
                )
            })

        modules = modulesPair.first
        modulesWithKtFiles = modulesPair.second
        chosenModuleType = if (modulesWithKtFiles.isEmpty()) {
            AtomicProperty(ChoseModuleType.ALL)
        } else {
            AtomicProperty(ChoseModuleType.CONTAINING_KT)
        }
        selectedModule = AtomicProperty(modules.firstOrNull())
    }

    fun notifyOnChange(f: () -> Unit) {
        selectedModule.afterChange { f() }
        chosenModuleType.afterChange { f() }
    }

    val modulesToConfigure: List<Module>
        get() = when (chosenModuleType.get()) {
            ChoseModuleType.ALL -> modules
            ChoseModuleType.CONTAINING_KT -> modulesWithKtFiles
            ChoseModuleType.SINGLE -> selectedModule.get()?.let { listOf(it) } ?: emptyList()
        }

    private fun Row.createKtModulesText() {
        if (modulesWithKtFiles.size > 2) {
            val firstName = modulesWithKtFiles[0].getName()
            val secondName = modulesWithKtFiles[1].getName()
            val message = message("choose.module.modules", firstName, secondName, modulesWithKtFiles.size - 2)
            cell(HyperlinkLabel(""))
                .applyToComponent {
                    setHtmlText("<html>$message")
                    addHyperlinkListener(HyperlinkListener {
                        val title = message("choose.module.modules.with.kotlin")
                        JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<Module>(title, modulesWithKtFiles) {
                            override fun getTextFor(value: Module): String {
                                return value.getName()
                            }
                        }).showUnderneathOf(this)
                    })
                }
        } else {
            text(modulesWithKtFiles.joinToString { it.name })
        }
    }

    private fun Cell<JBRadioButton>.applySelected(v: ChoseModuleType): Cell<JBRadioButton> {
        return onChanged {
            if (it.isSelected) {
                chosenModuleType.set(v)
            }
        }
    }

    fun createPanel(): DialogPanel {
        return panel {
            buttonsGroup {
                twoColumnsRow(
                    {
                        radioButton(message("all.modules"), ChoseModuleType.ALL)
                            .applySelected(ChoseModuleType.ALL)
                    }
                )
                twoColumnsRow(
                    {
                        radioButton(message("all.modules.containing.kotlin.files"), ChoseModuleType.CONTAINING_KT)
                            .applySelected(ChoseModuleType.CONTAINING_KT)
                    },
                    {
                        createKtModulesText()
                    }
                ).visible(modulesWithKtFiles.isNotEmpty())
                lateinit var singleModuleButton: Cell<JBRadioButton>
                twoColumnsRow(
                    {
                        singleModuleButton = radioButton(message("single.module"), ChoseModuleType.SINGLE)
                            .applySelected(ChoseModuleType.SINGLE)
                    },
                    {
                        val comboBox = comboBox(modules as List<Module?>, textListCellRenderer { it?.name })
                            .bindItem(selectedModule)
                            .align(AlignX.FILL)
                        comboBox.enabledIf(singleModuleButton.selected)
                    }
                )
            }.bind(chosenModuleType::get, chosenModuleType::set)
        }
    }
}