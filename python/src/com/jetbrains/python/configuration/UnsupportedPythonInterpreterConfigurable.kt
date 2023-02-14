// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus

/**
 * This configurable does not contain any information. It is used when Python interpreter's additional data is not recognized and, thus,
 * cannot be properly edited.
 */
@ApiStatus.Internal
internal class UnsupportedPythonInterpreterConfigurable(sdk: Sdk) : BoundConfigurable(sdk.name) {
  override fun createPanel(): DialogPanel = panel {}
}