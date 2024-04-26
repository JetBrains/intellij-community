// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ui

import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBComboBoxLabel
import com.intellij.ui.components.editors.JBComboBoxTableCellEditorComponent
import com.intellij.util.ui.AbstractTableCellEditor
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.Parameter
import org.jetbrains.kotlin.idea.refactoring.introduce.ui.AbstractParameterTablePanel
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.types.Variance

import java.awt.Component
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

abstract class FirExtractFunctionParameterTablePanel :
    AbstractParameterTablePanel<Parameter, FirExtractFunctionParameterTablePanel.ParameterInfo>() {
    companion object {
        const val PARAMETER_TYPE_COLUMN = 2
    }

    class ParameterInfo(
        originalParameter: Parameter,
        val isReceiver: Boolean
    ) : AbstractParameterInfo<Parameter>(originalParameter) {
        var type = originalParameter.parameterType

        init {
            name = if (isReceiver) KotlinBundle.message("text.receiver") else originalParameter.name
        }

        override fun toParameter() = object : Parameter by originalParameter {
            override val name: String = this@ParameterInfo.name
            override val parameterType: KtType = this@ParameterInfo.type
        }
    }

    abstract val context: KtElement

    override fun createTableModel(): TableModelBase = MyTableModel()

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun createAdditionalColumns() {
        with(table.columnModel.getColumn(PARAMETER_TYPE_COLUMN)) {
            headerValue = KotlinBundle.message("text.type")
            cellRenderer = object : DefaultTableCellRenderer() {
                private val myLabel = JBComboBoxLabel()

                override fun getTableCellRendererComponent(
                    table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
                ): Component {
                    @NlsSafe val renderType =
                        allowAnalysisOnEdt { analyze(context) { (value as KtType).render(position = Variance.IN_VARIANCE) } }
                    myLabel.text = renderType
                    myLabel.background = if (isSelected) table.selectionBackground else table.background
                    myLabel.foreground = if (isSelected) table.selectionForeground else table.foreground
                    if (isSelected) {
                        myLabel.setSelectionIcon()
                    } else {
                        myLabel.setRegularIcon()
                    }
                    return myLabel
                }
            }
            cellEditor = object : AbstractTableCellEditor() {
                val myEditorComponent = JBComboBoxTableCellEditorComponent()

                override fun getCellEditorValue() = myEditorComponent.editorValue

                override fun getTableCellEditorComponent(
                    table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int
                ): Component {
                    val info = parameterInfos[row]

                    myEditorComponent.setCell(table, row, column)
                    myEditorComponent.setOptions(*info.originalParameter.getParameterTypeCandidates().toTypedArray())
                    myEditorComponent.setDefaultValue(info.type)
                    myEditorComponent.setToString {
                        analyze(context) { (it as KtType).render(position = Variance.IN_VARIANCE) }
                    }

                    return myEditorComponent
                }
            }
        }
    }

    fun init(receiver: Parameter?, parameters: List<Parameter>) {
        parameterInfos = parameters.mapTo(
            if (receiver != null) arrayListOf(ParameterInfo(receiver, true)) else arrayListOf()
        ) { ParameterInfo(it, false) }

        super.init()
    }

    private inner class MyTableModel : TableModelBase() {
        override fun getColumnCount() = 3

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            if (columnIndex == PARAMETER_TYPE_COLUMN) return parameterInfos[rowIndex].type
            return super.getValueAt(rowIndex, columnIndex)
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex == PARAMETER_TYPE_COLUMN) {
                parameterInfos[rowIndex].type = aValue as KtType
                updateSignature()
                return
            }

            super.setValueAt(aValue, rowIndex, columnIndex)
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            val info = parameterInfos[rowIndex]
            return when (columnIndex) {
                PARAMETER_NAME_COLUMN -> super.isCellEditable(rowIndex, columnIndex) && !info.isReceiver
                PARAMETER_TYPE_COLUMN -> isEnabled && info.isEnabled && info.originalParameter.getParameterTypeCandidates().size > 1
                else -> super.isCellEditable(rowIndex, columnIndex)
            }
        }
    }

    val selectedReceiverInfo: ParameterInfo?
        get() = parameterInfos.singleOrNull { it.isEnabled && it.isReceiver }

    val selectedParameterInfos: List<ParameterInfo>
        get() = parameterInfos.filter { it.isEnabled && !it.isReceiver }
}
