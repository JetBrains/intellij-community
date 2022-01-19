// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.util

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

internal fun PsiElement.hasType(type: IElementType) = node.elementType == type
internal fun PsiElement.hasType(type: TokenSet) = node.elementType in type

internal fun ASTNode.hasType(type: IElementType) = elementType == type
internal fun ASTNode.hasType(type: TokenSet) = elementType in type
