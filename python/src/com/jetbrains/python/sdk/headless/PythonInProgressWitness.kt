// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.headless

import com.intellij.platform.backend.observation.MarkupBasedActivityInProgressWitness

class PythonInProgressWitness : MarkupBasedActivityInProgressWitness() {

  override val presentableName: String = "python"

}