// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.python

import com.intellij.python.community.execService.RelativePath

/**
 * A helper file as a [RelativePath] from the helpers root, for example:
 * ```kotlin
 * PyHelper("myhelper.py")
 * RelativePath { "myhelpers" / "file.py" }
 * ```
 */
typealias PyHelper = RelativePath