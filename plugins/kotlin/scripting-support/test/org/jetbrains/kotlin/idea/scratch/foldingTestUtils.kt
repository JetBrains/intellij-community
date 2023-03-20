// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.scratch

import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl

fun getFoldingData(editor: Editor, withCollapseStatus: Boolean): String {
    return CodeInsightTestFixtureImpl.getFoldingData(editor, withCollapseStatus)
}
