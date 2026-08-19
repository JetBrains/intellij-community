// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml

import com.intellij.platform.syntax.psi.SyntaxFileElementType
import com.intellij.psi.tree.IFileElementType

@JvmField
val YAML_FILE: IFileElementType = SyntaxFileElementType(YAMLLanguage.INSTANCE)