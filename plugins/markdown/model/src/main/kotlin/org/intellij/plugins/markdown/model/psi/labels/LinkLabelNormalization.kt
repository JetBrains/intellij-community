// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.model.psi.labels

internal fun normalizeLinkLabel(label: String): String = SPACES_REGEX.replace(label, " ").lowercase()

private val SPACES_REGEX = Regex("\\s+")
