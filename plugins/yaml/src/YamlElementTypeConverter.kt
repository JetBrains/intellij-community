// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml

import com.intellij.platform.syntax.psi.ElementTypeConverter
import com.intellij.platform.syntax.psi.ElementTypeConverters

internal fun getYamlElementTypeConverter(): ElementTypeConverter = ElementTypeConverters.getConverter(YAMLLanguage.INSTANCE)