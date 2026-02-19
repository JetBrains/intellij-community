// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion

/**
 * This data class retains types and methods about particular expressions associated with certain module usages.
 * @see moduleToMethods
 */
internal data class RuntimeCompletionMethods(val requiredTypes: List<String>?, val methodNames: List<String>)