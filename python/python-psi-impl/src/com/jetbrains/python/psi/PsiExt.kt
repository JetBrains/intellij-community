// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.FileContextUtil
import org.jetbrains.annotations.ApiStatus
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

@ApiStatus.Internal
fun PsiFile.injectionParent(): PsiElement? =
  getUserData(FileContextUtil.INJECTED_IN_ELEMENT)?.element

@ApiStatus.Internal
fun TomlLiteral.getStringOrNull(): String? =
  when (val kind = kind) {
    is TomlLiteralKind.String -> kind.value
    else -> null
  }
