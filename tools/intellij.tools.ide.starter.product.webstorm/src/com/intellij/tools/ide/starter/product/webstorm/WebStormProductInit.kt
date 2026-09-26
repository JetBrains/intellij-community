// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.product.webstorm

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.IdeProductInit
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * WebStorm [IdeInfo] resolved from DI.
 *
 * Tests that need WebStorm should depend on this module `intellij.tools.ide.starter.product.webstorm`
 */
val IdeInfo.Companion.WebStorm: IdeInfo
  get() {
    return di.direct.instance<IdeInfo>(tag = IdeInfoType.WEBSTORM)
  }

internal val DefaultWebStorm = IdeInfo(
  productCode = "WS",
  platformPrefix = "WebStorm",
  executableFileName = "webstorm",
  fullName = "WebStorm",
  qodanaProductCode = "QDJS"
)

/**
 * Registers WebStorm [IdeInfo] in DI and initializes Dev Build Server support.
 */
class WebStormProductInit : IdeProductInit {
  override val ideInfoType: IdeInfoType = IdeInfoType.WEBSTORM
  override val ideInfo: IdeInfo = DefaultWebStorm
}
