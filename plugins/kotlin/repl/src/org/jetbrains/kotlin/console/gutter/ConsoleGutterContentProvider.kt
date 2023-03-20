// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.console.gutter

import com.intellij.execution.console.BasicGutterContentProvider
import com.intellij.openapi.editor.Editor

class ConsoleGutterContentProvider : BasicGutterContentProvider() {
    /**
     *  This method overriding is needed to prevent [BasicGutterContentProvider] from adding some strange unicode
     *  symbols of zero width and to ease range highlighting.
     */
    override fun beforeEvaluate(editor: Editor) = Unit
}