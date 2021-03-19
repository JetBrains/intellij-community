// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface RestOutputHandler {
  fun apply(output: String): String

  companion object {
    val EP_NAME: ExtensionPointName<RestOutputHandler> = ExtensionPointName.create("restructured.text.html.preview.output.handler")
  }
}
