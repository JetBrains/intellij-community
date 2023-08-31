// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.framework.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.observable.util.or
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.uiDesigner.core.Spacer
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import java.awt.Dimension
import javax.swing.DefaultComboBoxModel


internal class KotlinVersionChooser(project: Project, minimumVersion: String): Disposable {
    private val loading = AtomicBooleanProperty(true)
    private val error = AtomicBooleanProperty(false)
    private val comboBoxModel = DefaultComboBoxModel<String>()
    private val selectedVersion = AtomicProperty("")

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    val kotlinVersion: String?
        get() = selectedVersion.get().takeIf { it.isNotBlank() }

    init {
        coroutineScope.launch {
            withBackgroundProgress(project, KotlinProjectConfigurationBundle.message("configure.kotlin.find.maven.versions")) {
                comboBoxModel.removeAllElements()
                loading.set(true)
                error.set(false)
                val kotlinVersions = try {
                    ConfigureDialogWithModulesAndVersion.loadVersions(minimumVersion)
                } catch (e: Exception) {
                    error.set(true)
                    listOf(ConfigureDialogWithModulesAndVersion.DEFAULT_KOTLIN_VERSION)
                }
                comboBoxModel.addAll(kotlinVersions)
                @Suppress("HardCodedStringLiteral")
                comboBoxModel.selectedItem = kotlinVersions.firstOrNull()
                loading.set(false)
            }
        }
    }

    fun runAfterVersionSelected(f: (String) -> Unit) {
        selectedVersion.afterChange(f)
    }

    fun runAfterVersionsLoaded(f: () -> Unit) {
        return loading.afterChange {
            if (!it) {
                f()
            }
        }
    }

    private class DummySpace : Spacer() {
        override fun getMinimumSize(): Dimension {
            return Dimension(8, 0)
        }
    }

    fun createPanel(): DialogPanel {
        return panel {
            row(KotlinProjectConfigurationBundle.message("kotlin.compiler.and.runtime.version")) {
                comboBox(comboBoxModel)
                    .resizableColumn()
                    .align(AlignX.FILL)
                    .customize(UnscaledGaps(0))
                    .bindItem(selectedVersion)
                    .enabledIf(loading.not())

                cell(DummySpace())
                    .visibleIf(loading.or(error))
                    .customize(UnscaledGaps(0))

                cell(AsyncProcessIcon("loader"))
                    .align(AlignX.RIGHT)
                    .visibleIf(loading)
                    .customize(UnscaledGaps(0))

                icon(UIUtil.getBalloonWarningIcon())
                    .align(AlignX.RIGHT)
                    .visibleIf(error)
                    .customize(UnscaledGaps(0))
                    .applyToComponent {
                        toolTipText = KotlinProjectConfigurationBundle.message("configure.kotlin.cant.load.versions")
                    }
            }.layout(RowLayout.PARENT_GRID)
        }
    }

    override fun dispose() {
        coroutineScope.cancel()
    }
}