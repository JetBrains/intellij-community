// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap

open class ExtractableCodeDescriptorWithConflictsResult
interface IExtractableCodeDescriptorWithConflicts<KotlinType> {
    val descriptor: IExtractableCodeDescriptor<KotlinType>
    val conflicts: MultiMap<PsiElement, String>
}

data class ExtractableCodeDescriptorWithException(val exception: RuntimeException) : ExtractableCodeDescriptorWithConflictsResult()