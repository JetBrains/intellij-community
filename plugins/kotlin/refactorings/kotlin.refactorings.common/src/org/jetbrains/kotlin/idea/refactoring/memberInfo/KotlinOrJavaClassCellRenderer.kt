// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiNamedElement
import com.intellij.ui.ListCellRendererWrapper
import javax.swing.JList

class KotlinOrJavaClassCellRenderer : ListCellRendererWrapper<PsiNamedElement?>() {
  override fun customize(list: JList<*>?, value: PsiNamedElement?, index: Int, selected: Boolean, hasFocus: Boolean) {
    if (value == null) return
    setText(value.qualifiedClassNameForRendering())
    val icon = value.getIcon(Iconable.ICON_FLAG_VISIBILITY or Iconable.ICON_FLAG_READ_STATUS)
    icon?.let { setIcon(it) }
  }
}