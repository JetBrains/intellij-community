// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard.ui

import com.intellij.java.library.getMavenCoordinates
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.tools.projectWizard.wizard.KotlinNewProjectWizardUIBundle
import javax.swing.JComponent
import javax.swing.ListCellRenderer

@ApiStatus.Internal
class StdlibVersionChooserDialog(
    project: Project,
    val availableLibraries: Map<@NlsSafe String, LibraryOrderEntry>
) : DialogWrapper(project) {

    private val selectedLibrary = AtomicProperty(LibraryNameAndVersion())

    private class LibraryNameAndVersion(@Nls val libraryName: String = "", @Nls val version: String = "")

    val library: String?
        get() = selectedLibrary.get().takeIf { it.libraryName.isNotBlank() }?.libraryName

    init {
        title = KotlinNewProjectWizardUIBundle.message("dialog.choose.stdlib")
        setCancelButtonText(KotlinNewProjectWizardUIBundle.message("dialog.use.default.library.button"))
        init()
    }

    fun getChosenLibrary(): LibraryOrderEntry? {
        return availableLibraries[library]
    }

    override fun createCenterPanel(): JComponent {
        val librariesNamesAndVersions = getLibrariesNamesAndVersions()
        val firstLibrary = if (librariesNamesAndVersions.isNotEmpty()) {
            librariesNamesAndVersions.first()
        } else {
            LibraryNameAndVersion()
        }
        selectedLibrary.set(LibraryNameAndVersion(firstLibrary.libraryName, firstLibrary.version))
        return panel {
            row(KotlinNewProjectWizardUIBundle.message("dialog.choose.stdlib.available.libs")) {
                comboBox(librariesNamesAndVersions, createRenderer()).bindItem(selectedLibrary)
            }
        }
    }

    private fun getLibrariesNamesAndVersions(): List<LibraryNameAndVersion> {
        val libraryNamesAndEntries = getLibraryNamesAndEntries()
        return libraryNamesAndEntries.toSortedMap().mapNotNull { (libraryName, libraryEntry) ->
            LibraryNameAndVersion(libraryName, libraryEntry.libraryVersion)
        }
    }

    internal class LibraryVersionAndOrderEntry(@Nls val libraryVersion: String, val library: LibraryOrderEntry)

    private fun getLibraryNamesAndEntries(): Map<String, LibraryVersionAndOrderEntry> {
        return availableLibraries.mapNotNull { (libraryName, libraryEntry) ->
            val version = libraryEntry.library?.getMavenCoordinates()?.version
            if (version == null) return@mapNotNull null
            libraryName to LibraryVersionAndOrderEntry(version, libraryEntry)
        }.toMap()
    }

    private fun createRenderer(): ListCellRenderer<LibraryNameAndVersion?> {
        return listCellRenderer {
            value?.let {
                val libraryName = StringUtil.shortenTextWithEllipsis(
                    it.libraryName, MAXIMUM_VISIBLE_LIBRARY_NAME, SUFFIX_LENGTH, USE_ELLIPSIS_SYMBOL
                )
                text(libraryName) {
                    align = LcrInitParams.Align.LEFT
                }
                text(it.version)
            }
        }
    }

    companion object {
        private const val MAXIMUM_VISIBLE_LIBRARY_NAME = 40
        private const val SUFFIX_LENGTH = 0
        private const val USE_ELLIPSIS_SYMBOL = true
    }
}