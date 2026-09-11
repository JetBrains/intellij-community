// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.externalSystem.service.ui.ExternalSystemJdkComboBox
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import javax.swing.JCheckBox
import javax.swing.JEditorPane

internal class MavenImportingSettingsUi(additionalSettings: Panel.() -> Unit) {

  lateinit var searchRecursivelyCheckBox: JCheckBox
  lateinit var autoDetectCompilerCheckBox: JCheckBox
  lateinit var excludeTargetFolderCheckBox: JCheckBox
  lateinit var useMavenOutputCheckBox: JCheckBox
  lateinit var runPluginsCompat: JCheckBox
  lateinit var generatedSourcesComboBox: ComboBox<MavenImportingSettings.GeneratedSourcesFolder>
  lateinit var updateFoldersOnImportPhaseComboBox: ComboBox<String>
  lateinit var downloadSourcesCheckBox: JCheckBox
  lateinit var downloadDocsCheckBox: JCheckBox
  lateinit var downloadAnnotationsCheckBox: JCheckBox
  lateinit var dependencyTypes: JBTextField
  lateinit var vmOptionsForImporter: JBTextField
  lateinit var jdkForImporterComboBox: ExternalSystemJdkComboBox
  lateinit var importerJdkWarning: JEditorPane

  @JvmField
  val panel = panel {
    row {
      searchRecursivelyCheckBox = checkBox(MavenConfigurableBundle.message("maven.settings.importing.search.recursively"))
        .component
    }

    row {
      autoDetectCompilerCheckBox = checkBox(MavenConfigurableBundle.message("maven.settings.importing.detect.compiler"))
        .visible(Registry.`is`("maven.import.compiler.arguments", true))
        .component
    }

    row {
      excludeTargetFolderCheckBox = checkBox(MavenConfigurableBundle.message("maven.settings.importing.project.exclude.build.directory"))
        .contextHelp(MavenConfigurableBundle.message("maven.settings.importing.project.exclude.build.directory.tooltip"))
        .component
    }

    row {
      useMavenOutputCheckBox = checkBox(MavenConfigurableBundle.message("maven.settings.importing.use.output.directories"))
        .contextHelp(MavenConfigurableBundle.message("maven.settings.importing.use.output.directories.tooltip"))
        .component
    }

    row {
      runPluginsCompat = checkBox(MavenConfigurableBundle.message("maven.settings.importing.use.plugin.compat"))
        .contextHelp(MavenConfigurableBundle.message("maven.settings.importing.use.plugin.compat.tooltip"))
        .visible(Registry.`is`("maven.use.plugins.m2e.compat"))
        .component
    }

    row(MavenConfigurableBundle.message("maven.settings.importing.generated.source.folders")) {
      generatedSourcesComboBox = comboBox(MavenImportingSettings.GeneratedSourcesFolder.entries,
                                          textListCellRenderer("") { it.title })
        .component
    }.layout(RowLayout.INDEPENDENT)

    row(MavenConfigurableBundle.message("maven.settings.importing.phase.for.source.updates")) {
      updateFoldersOnImportPhaseComboBox = comboBox(MavenImportingSettings.UPDATE_FOLDERS_PHASES.asList())
        .comment(MavenConfigurableBundle.message("maven.settings.importing.phase.for.source.updates.notes"))
        .component
    }.layout(RowLayout.INDEPENDENT)

    row(MavenConfigurableBundle.message("maven.settings.importing.auto.download")) {
      downloadSourcesCheckBox = checkBox(MavenConfigurableBundle.message("maven.settings.importing.auto.download.sources"))
        .component
      downloadDocsCheckBox = checkBox(MavenConfigurableBundle.message("maven.settings.importing.auto.download.documentation"))
        .component
      downloadAnnotationsCheckBox = checkBox(MavenConfigurableBundle.message("maven.settings.importing.auto.download.annotations"))
        .component
    }.topGap(TopGap.MEDIUM)

    row(MavenConfigurableBundle.message("maven.settings.importing.dependency.type")) {
      dependencyTypes = textField()
        .align(AlignX.FILL)
        .comment(MavenConfigurableBundle.message("maven.settings.importing.dependency.type.tooltip"))
        .component
    }

    row(MavenConfigurableBundle.message("maven.settings.importing.vm.options")) {
      vmOptionsForImporter = textField()
        .align(AlignX.FILL)
        .comment(MavenConfigurableBundle.message("maven.settings.vm.options.tooltip"))
        .component
    }

    row(MavenConfigurableBundle.message("maven.settings.importing.jdk")) {
      val jdkCell = cell(ExternalSystemJdkComboBox())
        .align(AlignX.FILL)
        .comment(MavenConfigurableBundle.message("maven.settings.importing.jdk.fallback.warning"))
      jdkForImporterComboBox = jdkCell.component
      importerJdkWarning = jdkCell.comment!!
    }

    additionalSettings()
  }
}
