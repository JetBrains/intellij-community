// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.psi

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType

open class MermaidLeafPsiElement(type: IElementType, text: CharSequence): LeafPsiElement(type, text), MermaidPsiElement
