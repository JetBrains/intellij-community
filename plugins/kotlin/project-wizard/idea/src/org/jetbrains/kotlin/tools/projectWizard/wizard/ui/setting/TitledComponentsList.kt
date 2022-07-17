// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.wizard.ui.setting

import com.intellij.ui.ContextHelpLabel
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.kotlin.tools.projectWizard.core.Context
import org.jetbrains.kotlin.tools.projectWizard.core.entity.ValidationResult
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.*
import javax.swing.JComponent
import javax.swing.Spring
import javax.swing.SpringLayout

open class TitledComponentsList(
    private var components: List<TitledComponent>,
    context: Context,
    private val stretchY: Boolean = false,
    private val globalMaxWidth: Int? = null,
    useBigYGap: Boolean = false,
    var xPadding: Int = xPanelPadding,
    var yPadding: Int = yPanelPadding
) : DynamicComponent(context) {
    private val ui = BorderLayoutPanel()

    private val yGap = if (useBigYGap) yGapBig else yGapSmall

    init {
        components.forEach(::registerSubComponent)
        ui.addToCenter(createComponentsPanel(components))
    }

    override val component get() = ui

    override fun navigateTo(error: ValidationResult.ValidationError) {
        components.forEach { it.navigateTo(error) }
    }

    override fun onInit() {
        super.onInit()
        components.forEach { it.onInit() }
    }

    fun setComponents(newComponents: List<TitledComponent>) {
        this.components = newComponents
        newComponents.forEach(::registerSubComponent)
        ui.removeAll()
        newComponents.forEach(TitledComponent::onInit)
        ui.addToCenter(createComponentsPanel(newComponents))
    }

    private fun createComponentsPanel(components: List<TitledComponent>) = customPanel(SpringLayout()) {
        if (components.isEmpty()) return@customPanel
        val layout = this.layout as SpringLayout

        fun JComponent.constraints() = layout.getConstraints(this)

        val componentsWithLabels = components.mapNotNull { component ->
            if (!component.shouldBeShown()) return@mapNotNull null
            val label = label(component.title?.let { "$it:" }.orEmpty())
            val tooltip = component.tooltipText?.let { ContextHelpLabel.create(component.tooltipText.orEmpty()) }

            TitledComponentData(
                label.also { add(it) }.constraints(),
                tooltip?.also { add(it) }?.constraints(),
                component.component.also { add(it) }.constraints(),
                component.alignment,
                component.additionalComponentPadding,
                component.maximumWidth
            )
        }

        fun TitledComponentData.centerConstraint(): Spring = when (alignment) {
            TitleComponentAlignment.AlignAgainstMainComponent -> component.height * .5f - label.height * .5f
            is TitleComponentAlignment.AlignAgainstSpecificComponent -> alignment.alignTarget.constraints().height * .5f - label.height * .5f
            is TitleComponentAlignment.AlignFormTopWithPadding -> alignment.padding.asSpring()
        }

        val maxLabelWidth =
            componentsWithLabels.fold(componentsWithLabels.firstOrNull()?.label?.width ?: Spring.constant(0)) { spring, row ->
                Spring.max(spring, row.label.width)
            }

        componentsWithLabels.forEach { (_, tooltipConst, component, _, _, componentMaxWidth) ->
            val maxWidth = componentMaxWidth ?: globalMaxWidth
            if (maxWidth == null) {
                component[SpringLayout.EAST] = layout.getConstraint(SpringLayout.EAST, this) - xPadding.asSpring()
            } else {
                component.width = maxWidth.asSpring()
            }
        }

        var lastLabel: SpringLayout.Constraints? = null
        var lastComponent: SpringLayout.Constraints? = null

        val tooltipWidth = componentsWithLabels.find { it.tooltip != null }?.tooltip?.width

        for (data in componentsWithLabels) {
            val (label, tooltip, component) = data
            label.x = xPadding.asSpring()
            tooltip?.x = label[SpringLayout.EAST] + xGap
            component.x = maxLabelWidth + 2 * xGap
            if (tooltipWidth != null)
                component.x += tooltipWidth + xGap

            if (lastComponent != null && lastLabel != null) {
                val constraint = lastComponent[SpringLayout.SOUTH] + yGap + data.additionalComponentGap
                label.y = constraint + data.centerConstraint()
                tooltip?.y = label.y
                component.y = constraint
            } else {
                label.y = data.centerConstraint() + yPadding
                tooltip?.y = label.y
                component.y = yPadding.asSpring()
            }

            lastLabel = label
            lastComponent = component
        }

        if (stretchY) {
            constraints()[SpringLayout.SOUTH] = lastComponent!![SpringLayout.SOUTH]
        }
    }

    companion object {
        private const val xGap = 5
        private const val yGapSmall = 6
        private const val yGapBig = 12
        private const val xPanelPadding = UIConstants.PADDING
        private const val yPanelPadding = UIConstants.PADDING
    }

    private data class TitledComponentData(
        val label: SpringLayout.Constraints,
        val tooltip: SpringLayout.Constraints?,
        val component: SpringLayout.Constraints,
        val alignment: TitleComponentAlignment,
        val additionalComponentGap: Int,
        val maximumWidth: Int?
    )
}

