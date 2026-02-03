// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.changeSignature.ui

import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.util.ui.ColumnInfo
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinModifiableMethodDescriptor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinModifiableParameterInfo
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

abstract class KotlinFunctionParameterTableModel<P: KotlinModifiableParameterInfo, V>(
    methodDescriptor: KotlinModifiableMethodDescriptor<P, V>,
    defaultValueContext: PsiElement
) : KotlinCallableParameterTableModel<P, V>(
  methodDescriptor,
  defaultValueContext,
  NameColumn<KotlinModifiableParameterInfo, ParameterTableModelItemBase<KotlinModifiableParameterInfo>>(defaultValueContext.project),
  TypeColumn<KotlinModifiableParameterInfo, ParameterTableModelItemBase<KotlinModifiableParameterInfo>>(defaultValueContext.project, KotlinFileType.INSTANCE),
  DefaultValueColumn<KotlinModifiableParameterInfo, ParameterTableModelItemBase<KotlinModifiableParameterInfo>>(
        defaultValueContext.project,
        KotlinFileType.INSTANCE
    ),
  DefaultParameterColumn(),
  ContextParameterColumn<KotlinModifiableParameterInfo>(),
  ReceiverColumn(methodDescriptor),
) {
    override fun removeRow(idx: Int) {
        if (getRowValue(idx).parameter == receiver) {
            receiver = null
        }

        super.removeRow(idx)
    }

    override var receiver: KotlinModifiableParameterInfo?
        get() = (columnInfos.last() as ReceiverColumn<*, *>).receiver
        set(receiver) {
            (columnInfos.last() as ReceiverColumn<*, *>).receiver = receiver
            receiver?.isContextParameter = false
        }

    private class ReceiverColumn<P: KotlinModifiableParameterInfo, V>(methodDescriptor: KotlinModifiableMethodDescriptor<P, V>) :
        ColumnInfoBase<P, ParameterTableModelItemBase<P>, Boolean>(KotlinBundle.message("column.name.receiver")) {
        var receiver: KotlinModifiableParameterInfo? = methodDescriptor.receiver
        override fun valueOf(item: ParameterTableModelItemBase<P>): Boolean = item.parameter == receiver

        override fun setValue(item: ParameterTableModelItemBase<P>, value: Boolean?) {
            if (value == null) return
            receiver = if (value) item.parameter else null
        }

        override fun isCellEditable(pParameterTableModelItemBase: ParameterTableModelItemBase<P>): Boolean = true

        override fun doCreateRenderer(item: ParameterTableModelItemBase<P>): TableCellRenderer =
            BooleanTableCellRenderer()

        override fun doCreateEditor(o: ParameterTableModelItemBase<P>): TableCellEditor =
            BooleanTableCellEditor()
    }

    private class ContextParameterColumn<P: KotlinModifiableParameterInfo>() :
        ColumnInfoBase<P, ParameterTableModelItemBase<P>, Boolean>(KotlinBundle.message("column.name.context.parameter")) {

        override fun valueOf(item: ParameterTableModelItemBase<P>): Boolean = item.parameter.isContextParameter

        override fun setValue(item: ParameterTableModelItemBase<P>, value: Boolean?) {
            if (value == null) return
            item.parameter.isContextParameter = value
        }

        override fun isCellEditable(pParameterTableModelItemBase: ParameterTableModelItemBase<P>): Boolean = true

        override fun doCreateRenderer(item: ParameterTableModelItemBase<P>): TableCellRenderer =
            BooleanTableCellRenderer()

        override fun doCreateEditor(o: ParameterTableModelItemBase<P>): TableCellEditor =
            BooleanTableCellEditor()
    }

    companion object {
        fun isReceiverColumn(column: ColumnInfo<*, *>?): Boolean = column is ReceiverColumn<*, *>
        fun isContextParameterColumn(column: ColumnInfo<*, *>?): Boolean = column is ContextParameterColumn<*>
    }
}