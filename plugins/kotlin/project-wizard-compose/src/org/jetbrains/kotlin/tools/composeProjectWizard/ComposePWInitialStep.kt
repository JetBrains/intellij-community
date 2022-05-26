// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.composeProjectWizard

import com.intellij.ide.starters.local.*
import com.intellij.ide.starters.local.wizard.StarterInitialStep
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.layout.*
import com.intellij.ui.layout.Row
import org.jetbrains.annotations.Nls
import javax.swing.*

class ComposePWInitialStep(contextProvider: StarterContextProvider) : StarterInitialStep(contextProvider) {

    private val mpConfigProperty: GraphProperty<ComposeConfigurationType> = propertyGraph.lazyProperty { ComposeConfigurationType.SINGLE_PLATFORM }
    private val platformItemProperty:  GraphProperty<ComposePlatform> = propertyGraph.lazyProperty { ComposePlatform.DESKTOP }

    private lateinit var platformRow: Row

    override fun addFieldsBefore(layout: LayoutBuilder) {
        layout.row(ComposeProjectWizardBundle.message("label.configuration")) {
            segmentedButton( //StarterInitialStep doesn't support Kotlin UI DSL Version 2
                listOf(ComposeConfigurationType.SINGLE_PLATFORM, ComposeConfigurationType.MULTI_PLATFORM), mpConfigProperty
            ) { it.messagePointer }
        }.largeGapAfter()

        starterContext.putUserData(ComposeModuleBuilder.COMPOSE_CONFIG_TYPE_KEY, ComposeConfigurationType.SINGLE_PLATFORM)

        mpConfigProperty.afterChange { configType ->
            platformRow.visible = configType == ComposeConfigurationType.SINGLE_PLATFORM
            starterContext.putUserData(ComposeModuleBuilder.COMPOSE_CONFIG_TYPE_KEY, configType)
        }

        layout.row(ComposeProjectWizardBundle.message("label.platform")) {
            platformRow = this
            comboBox(model = CollectionComboBoxModel(listOf(ComposePlatform.DESKTOP, ComposePlatform.WEB)), property = platformItemProperty, renderer = PlaformRenderer())
        }.largeGapAfter()
        starterContext.putUserData(ComposeModuleBuilder.COMPOSE_PLATFORM_KEY, ComposePlatform.DESKTOP)

        platformItemProperty.afterChange {
            starterContext.putUserData(ComposeModuleBuilder.COMPOSE_PLATFORM_KEY, it)
        }
    }

    override fun addFieldsAfter(layout: LayoutBuilder) {
        layout.row {
            hyperLink(ComposeProjectWizardBundle.message("compose.getting_started"),
                      "https://github.com/JetBrains/compose-jb/tree/master/tutorials/Getting_Started/README.md")
        }

        artifactRow.visible = false
    }

    enum class ComposeConfigurationType(
        val messagePointer: String
    ) {
        SINGLE_PLATFORM(ComposeProjectWizardBundle.message("label.single_platform")),
        MULTI_PLATFORM(ComposeProjectWizardBundle.message("label.multiple_platforms"))
    }

    enum class ComposePlatform(
        val messagePointer: String
    ) {
        DESKTOP(ComposeProjectWizardBundle.message("label.platform.desktop")),
        WEB(ComposeProjectWizardBundle.message("label.platform.web"))
    }

    private class PlaformRenderer : ColoredListCellRenderer<ComposePlatform>() {
        override fun customizeCellRenderer(list: JList<out ComposePlatform>,
                                           value: ComposePlatform?,
                                           index: Int,
                                           selected: Boolean,
                                           hasFocus: Boolean) {
            val catalog = value ?: return
            append(catalog.messagePointer)
        }
    }

    private fun Row.hyperLink(@Nls title: String, @NlsSafe url: String) {
        val hyperlinkLabel = HyperlinkLabel(title)
        hyperlinkLabel.setHyperlinkTarget(url)
        hyperlinkLabel.toolTipText = url
        this.component(hyperlinkLabel)
    }
}