// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.composeProjectWizard

import com.intellij.ide.starters.local.*
import com.intellij.ide.starters.local.wizard.StarterInitialStep
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.layout.*
import com.intellij.ui.layout.Row
import org.jetbrains.annotations.Nls
import javax.swing.*

class ComposePWInitialStep(contextProvider: StarterContextProvider) : StarterInitialStep(contextProvider) {

    private val mpConfigProperty: GraphProperty<ComposeConfigurationType> = propertyGraph.graphProperty { ComposeConfigurationType.SINGLE_PLATFORM }
    private val platformItemProperty:  GraphProperty<ComposePlatform> = propertyGraph.graphProperty { ComposePlatform.DESKTOP }
    //private val mpPlatformsProperty:  GraphProperty<List<ComposePlatform>> = propertyGraph.graphProperty { emptyList() } //for future use

    protected lateinit var platformRow: Row
    protected lateinit var mpPlatformsRow: Row

    private fun suggestLocationByName(): String {
        return wizardContext.projectFileDirectory // no project name included
    }

    override fun addFieldsBefore(layout: LayoutBuilder) {
        layout.row(ComposeProjectWizardBundle.message("label.configuration")) {
            segmentedButton(
                listOf(ComposeConfigurationType.SINGLE_PLATFORM, ComposeConfigurationType.MULTI_PLATFORM),
                mpConfigProperty
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

        //for now there is only one mpp template, so this is stub for future
        //layout.row("Platforms") {
        //    mpPlatformsRow = this
        //    val list = CheckboxTreeBase()
        //    val platformsRoot = CheckedTreeNode()
        //    val libraryNode = CheckedTreeNode("Desktop")
        //    platformsRoot.add(libraryNode)
        //    list.model = DefaultTreeModel(platformsRoot)
        //    component(JPanel(GridBagLayout()).apply {
        //        add(list)
        //    })
        //}.largeGapAfter()


    }

    override fun addFieldsAfter(layout: LayoutBuilder) {
        layout.row {
            hyperLink(ComposeProjectWizardBundle.message("compose.getting_started"),
                      "https://github.com/JetBrains/compose-jb/tree/master/tutorials/Getting_Started/README.md")
        }

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
        DESKTOP("Desktop"),
        ANDROID("Android"),
        WEB("Web")
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


    //this will be needed later when mpp template is more flexible
    /*private fun createPlatformList(): CheckboxTreeBase {
        val list = CheckboxTreeBase(object : CheckboxTree.CheckboxTreeCellRenderer() {
            override fun customizeRenderer(
                tree: JTree?,
                value: Any?,
                selected: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ) {
                if (value !is DefaultMutableTreeNode) return

                this.border = JBUI.Borders.empty(2)
                val renderer = textRenderer

                when (val item = value.userObject) {
                    is LibraryCategory -> {
                        renderer.append(item.title, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    }
                }
            }
        }, null)

        //enableEnterKeyHandling(list)

        list.rowHeight = 0
        list.isRootVisible = false
        list.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        list.addCheckboxTreeListener(object : CheckboxTreeListener {
            override fun nodeStateChanged(node: CheckedTreeNode) {
                val library = node.userObject as? Library ?: return
                val libraryId = library.id
                if (node.isChecked) {
                    //selectedLibraryIds.add(libraryId)
                    //selectedLibraryIds.removeAll(library.includesLibraries)
                }
                else {
                    //selectedLibraryIds.remove(libraryId)
                }
                //updateIncludedLibraries(library, node)
                //updateSelectedLibraries()
                list.repaint()
            }
        })

        return list
    }*/


}