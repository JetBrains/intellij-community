// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.completion

import com.intellij.lang.WordCompletionElementFilter
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.yaml.YAMLElementTypes
import org.jetbrains.yaml.YAMLTokenTypes


private class YamlWordCompletionFilter : WordCompletionElementFilter {

  private val tokenSet = TokenSet.create(YAMLTokenTypes.SCALAR_KEY, YAMLTokenTypes.COMMENT, *YAMLElementTypes.SCALAR_VALUES.types)

  override fun isWordCompletionEnabledIn(element: IElementType): Boolean = tokenSet.contains(element)
}
