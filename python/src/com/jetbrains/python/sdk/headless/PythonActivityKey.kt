// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.headless

import com.intellij.platform.backend.observation.ActivityKey
import com.jetbrains.python.PyBundle
import org.jetbrains.annotations.Nls

object PythonActivityKey : ActivityKey {
  override val presentableName: @Nls String
    get() = PyBundle.message("python.activity.key.name")
}