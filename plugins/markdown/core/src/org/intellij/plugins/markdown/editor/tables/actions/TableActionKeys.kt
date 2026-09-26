// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.psi.PsiElement
import java.lang.ref.WeakReference

internal object TableActionKeys {
  val ELEMENT = DataKey.create<WeakReference<PsiElement>>("TableBarElement")
  val COLUMN_INDEX = DataKey.create<Int>("TableBarColumnIndex")

}
