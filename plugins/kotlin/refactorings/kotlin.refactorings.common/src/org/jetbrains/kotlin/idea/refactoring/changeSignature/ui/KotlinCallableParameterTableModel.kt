// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.changeSignature.ui

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ParameterTableModelBase
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.util.ui.ColumnInfo
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.changeSignature.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

abstract class KotlinCallableParameterTableModel<P: KotlinModifiableParameterInfo, V> protected constructor(
    val methodDescriptor: KotlinModifiableMethodDescriptor<P, V>,
    defaultValueContext: PsiElement,
    vararg columnInfos: ColumnInfo<*, *>?
) : ParameterTableModelBase<P, ParameterTableModelItemBase<P>>(
  getTypeCodeFragmentContext(methodDescriptor.baseDeclaration),
  defaultValueContext,
  *columnInfos,
) {
    val project: Project = defaultValueContext.project

    open var receiver: KotlinModifiableParameterInfo? = null

    protected class DefaultParameterColumn :
        ColumnInfoBase<KotlinModifiableParameterInfo, ParameterTableModelItemBase<KotlinModifiableParameterInfo>, Boolean>(KotlinBundle.message("column.name.default.parameter")) {
        override fun isCellEditable(pParameterTableModelItemBase: ParameterTableModelItemBase<KotlinModifiableParameterInfo>): Boolean = true

        override fun doCreateRenderer(item: ParameterTableModelItemBase<KotlinModifiableParameterInfo>): TableCellRenderer =
            BooleanTableCellRenderer()

        override fun doCreateEditor(o: ParameterTableModelItemBase<KotlinModifiableParameterInfo>): TableCellEditor =
            BooleanTableCellEditor()

        override fun valueOf(item: ParameterTableModelItemBase<KotlinModifiableParameterInfo>): Boolean =
            item.parameter.defaultValueAsDefaultParameter

        override fun setValue(item: ParameterTableModelItemBase<KotlinModifiableParameterInfo>, value: Boolean?) {
            item.parameter.defaultValueAsDefaultParameter = value == true
        }
    }

    companion object {
        fun isTypeColumn(column: ColumnInfo<*, *>?): Boolean = column is TypeColumn<*, *>

        fun isNameColumn(column: ColumnInfo<*, *>?): Boolean = column is NameColumn<*, *>

        fun isDefaultValueColumn(column: ColumnInfo<*, *>?): Boolean = column is DefaultValueColumn<*, *>

        fun isDefaultParameterColumn(column: ColumnInfo<*, *>?): Boolean = column is DefaultParameterColumn

        fun getTypeCodeFragmentContext(startFrom: PsiElement): KtElement = startFrom.parentsWithSelf.mapNotNull {
            when {
                it is KtNamedFunction -> it.bodyExpression ?: it.valueParameterList
                it is KtPropertyAccessor -> it.bodyExpression
                it is KtDeclaration && KtPsiUtil.isLocal(it) -> null
                it is KtConstructor<*> -> it
                it is KtClassOrObject -> it
                it is KtFile -> it
                else -> null
            }
        }.first()
    }

}