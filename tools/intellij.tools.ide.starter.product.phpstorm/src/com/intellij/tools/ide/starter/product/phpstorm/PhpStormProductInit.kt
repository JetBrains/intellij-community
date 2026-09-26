// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.product.phpstorm

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.IdeProductInit
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * PhpStorm [IdeInfo] resolved from DI.
 *
 * Tests that need PhpStorm should depend on this module `intellij.tools.ide.starter.product.phpstorm`
 */
val IdeInfo.Companion.PhpStorm: IdeInfo
  get() {
    return di.direct.instance<IdeInfo>(tag = IdeInfoType.PHPSTORM)
  }

internal val DefaultPhpStorm = IdeInfo(
  productCode = "PS",
  platformPrefix = "PhpStorm",
  executableFileName = "phpstorm",
  fullName = "PhpStorm",
  qodanaProductCode = "QDPHP"
)

/**
 * Registers PhpStorm [IdeInfo] in DI.
 */
class PhpStormProductInit : IdeProductInit {
  override val ideInfoType: IdeInfoType = IdeInfoType.PHPSTORM
  override val ideInfo: IdeInfo = DefaultPhpStorm
}
