// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement

/**
 * @param lookupElement - completion item
 * @param prefix - prefix for prefix matcher corresponding to lookupElement
 */
data class RuntimeLookupElement(val lookupElement: LookupElement, val prefix: PrefixMatcher)