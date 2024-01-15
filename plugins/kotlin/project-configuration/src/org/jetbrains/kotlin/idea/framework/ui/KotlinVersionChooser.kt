// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.framework.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.observable.util.or
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.namedChildScope
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.uiDesigner.core.Spacer
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import java.awt.Dimension
import javax.swing.DefaultComboBoxModel


@Service(Service.Level.PROJECT)
internal class KotlinVersionChooserService(
    private val coroutineScope: CoroutineScope
) {
    fun childScope(name: String): CoroutineScope = coroutineScope.namedChildScope(name)
}

internal class KotlinVersionChooser(
    private val project: Project,
    private val minimumVersion: String,
    private val parentDisposable: Disposable,
    private val modalityState: ModalityState
) {
    private val loading = AtomicBooleanProperty(true)
    private val error = AtomicBooleanProperty(false)
    private val comboBoxModel = DefaultComboBoxModel<String>()
    private val selectedVersion = AtomicProperty("")

    private val coroutineScope = project.service<KotlinVersionChooserService>().childScope("KotlinVersionChooser")

    val kotlinVersion: String?
        get() = selectedVersion.get().takeIf { it.isNotBlank() }

    init {
        Disposer.register(parentDisposable) {
            coroutineScope.cancel()
        }

        coroutineScope.launch(Dispatchers.EDT + modalityState.asContextElement()) {
            // Use IO dispatcher because loadVersions is blocking
            val loadedVersions = withContext(Dispatchers.IO) {
                runCatching {
                    ConfigureDialogWithModulesAndVersion.loadVersions(minimumVersion)
                }.getOrNull()
            }
            error.set(loadedVersions == null)

            val kotlinVersions = loadedVersions ?: listOf(ConfigureDialogWithModulesAndVersion.DEFAULT_KOTLIN_VERSION)
            comboBoxModel.addAll(kotlinVersions)
            @Suppress("HardCodedStringLiteral")
            comboBoxModel.selectedItem = kotlinVersions.firstOrNull()
            loading.set(false)
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
}