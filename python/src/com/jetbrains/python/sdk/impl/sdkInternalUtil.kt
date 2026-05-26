// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.jetbrains.python.ui.pyMayBeModalBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.path.pathString

internal fun getBasePythonsPaths(): List<@NlsSafe String> =
  pyMayBeModalBlocking(ModalTaskOwner.guess()) {
    SystemPythonService().findSystemPythons().map { withContext(Dispatchers.IO) { it.pythonBinary.toRealPath() }.pathString }.sorted()
  }