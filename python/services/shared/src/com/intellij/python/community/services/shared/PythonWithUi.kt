// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.shared

/**
 * Python that has both [languageLevel] and [ui]
 */
interface PythonWithUi : PythonWithLanguageLevel, UiHolder