// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.platform.workspace.jps.serialization.CustomImlComponentNameContributor
import com.jetbrains.python.ReSTService.MODULE_STATE_COMPONENT

internal class ReSTServiceImlComponentNameContributor: CustomImlComponentNameContributor {
  override val componentName: String = MODULE_STATE_COMPONENT
}
