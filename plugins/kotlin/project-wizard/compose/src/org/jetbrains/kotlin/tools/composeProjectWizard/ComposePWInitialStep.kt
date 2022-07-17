// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.composeProjectWizard

import com.intellij.ide.starters.local.StarterContextProvider
import com.intellij.ide.starters.local.wizard.StarterInitialStep
import com.intellij.ide.starters.shared.hyperLink
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindItem
import org.jetbrains.annotations.Nls
import java.util.function.Supplier
import javax.swing.JList

class ComposePWInitialStep(contextProvider: StarterContextProvider) : StarterInitialStep(contextProvider) {

    private val mpConfigProperty: GraphProperty<ComposeConfigurationType> =
        propertyGraph.lazyProperty { ComposeConfigurationType.SINGLE_PLATFORM }
    private val platformItemProperty: GraphProperty<ComposePlatform> = propertyGraph.lazyProperty { ComposePlatform.DESKTOP }

    private lateinit var platformRow: Row

    override fun addFieldsBefore(layout: Panel) {
        layout.row(ComposeProjectWizardBundle.message("label.configuration")) {
            val items = listOf(ComposeConfigurationType.SINGLE_PLATFORM, ComposeConfigurationType.MULTI_PLATFORM)
            segmentedButton(items) { it.messagePointer.get() }
                .bind(mpConfigProperty)
        }.bottomGap(BottomGap.SMALL)

        starterContext.putUserData(ComposeModuleBuilder.COMPOSE_CONFIG_TYPE_KEY, ComposeConfigurationType.SINGLE_PLATFORM)

        mpConfigProperty.afterChange { configType ->
            platformRow.visible(configType == ComposeConfigurationType.SINGLE_PLATFORM)
            starterContext.putUserData(ComposeModuleBuilder.COMPOSE_CONFIG_TYPE_KEY, configType)
        }

        layout.row(ComposeProjectWizardBundle.message("label.platform")) {
            platformRow = this

            comboBox(listOf(ComposePlatform.DESKTOP, ComposePlatform.WEB), PlatformRenderer())
                .bindItem(platformItemProperty)
        }.bottomGap(BottomGap.SMALL)
        starterContext.putUserData(ComposeModuleBuilder.COMPOSE_PLATFORM_KEY, ComposePlatform.DESKTOP)

        platformItemProperty.afterChange {
            starterContext.putUserData(ComposeModuleBuilder.COMPOSE_PLATFORM_KEY, it)
        }
    }

    override fun addFieldsAfter(layout: Panel) {
        layout.row {
            hyperLink(
                ComposeProjectWizardBundle.message("compose.getting_started"),
                "https://github.com/JetBrains/compose-jb/tree/master/tutorials/Getting_Started/README.md"
            )
        }
    }

    enum class ComposeConfigurationType(val messagePointer: Supplier<@Nls String>) {
        SINGLE_PLATFORM(ComposeProjectWizardBundle.messagePointer("label.single_platform")),
        MULTI_PLATFORM(ComposeProjectWizardBundle.messagePointer("label.multiple_platforms"))
    }

    enum class ComposePlatform(val messagePointer: Supplier<@Nls String>) {
        DESKTOP(ComposeProjectWizardBundle.messagePointer("label.platform.desktop")),
        WEB(ComposeProjectWizardBundle.messagePointer("label.platform.web"))
    }

    private class PlatformRenderer : ColoredListCellRenderer<ComposePlatform>() {
        override fun customizeCellRenderer(
            list: JList<out ComposePlatform>,
            value: ComposePlatform?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            val catalog = value ?: return
            append(catalog.messagePointer.get())
        }
    }
}