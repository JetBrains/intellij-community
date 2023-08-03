// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.changeSignature.ui

import com.intellij.openapi.ui.ComboBoxTableRenderer
import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.util.ui.ColumnInfo
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinModifiableMethodDescriptor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinModifiableParameterInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import javax.swing.DefaultCellEditor
import javax.swing.JComboBox
import javax.swing.JTable
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

abstract class KotlinPrimaryConstructorParameterTableModel<P: KotlinModifiableParameterInfo, V>(
    methodDescriptor: KotlinModifiableMethodDescriptor<P, V>,
    defaultValueContext: PsiElement
) : KotlinCallableParameterTableModel<P, V>(
  methodDescriptor,
  defaultValueContext,
  ValVarColumn(),
  NameColumn<P, ParameterTableModelItemBase<P>>(defaultValueContext.project),
  TypeColumn<P, ParameterTableModelItemBase<P>>(defaultValueContext.project, KotlinFileType.INSTANCE),
  DefaultValueColumn<P, ParameterTableModelItemBase<P>>(
        defaultValueContext.project,
        KotlinFileType.INSTANCE,
    ),
  DefaultParameterColumn(),
) {
    private class ValVarColumn : ColumnInfoBase<KotlinModifiableParameterInfo, ParameterTableModelItemBase<KotlinModifiableParameterInfo>, KotlinValVar>(
        KotlinBundle.message("column.name.val.var")
    ) {
        override fun isCellEditable(item: ParameterTableModelItemBase<KotlinModifiableParameterInfo>): Boolean {
            return !item.isEllipsisType && item.parameter.isNewParameter
        }

        override fun valueOf(item: ParameterTableModelItemBase<KotlinModifiableParameterInfo>): KotlinValVar = item.parameter.valOrVar

        override fun setValue(item: ParameterTableModelItemBase<KotlinModifiableParameterInfo>, value: KotlinValVar) {
            item.parameter.valOrVar = value
        }

        override fun doCreateRenderer(item: ParameterTableModelItemBase<KotlinModifiableParameterInfo>): TableCellRenderer {
            return ComboBoxTableRenderer(KotlinValVar.values())
        }

        override fun doCreateEditor(item: ParameterTableModelItemBase<KotlinModifiableParameterInfo>): TableCellEditor {
            return DefaultCellEditor(JComboBox<ParameterTableModelItemBase<KotlinModifiableParameterInfo>>())
        }

        override fun getWidth(table: JTable): Int = table.getFontMetrics(table.font).stringWidth(name) + 8
    }

    companion object {
        fun isValVarColumn(column: ColumnInfo<*, *>?): Boolean = column is ValVarColumn
    }
}