// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.product.rustrover

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.IdeProductInit
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * RustRover [IdeInfo] resolved from DI.
 *
 * Tests that need RustRover should depend on this module `intellij.tools.ide.starter.product.rustrover`
 */
val IdeInfo.Companion.RustRover: IdeInfo
  get() {
    return di.direct.instance<IdeInfo>(tag = IdeInfoType.RUSTROVER)
  }

internal val DefaultRustRover = IdeInfo(
  productCode = "RR",
  platformPrefix = "RustRover",
  executableFileName = "rustrover",
  fullName = "RustRover",
  qodanaProductCode = "QDRST"
)

/**
 * Registers RustRover [IdeInfo] in DI and initializes Dev Build Server support.
 */
class RustRoverProductInit : IdeProductInit {
  override val ideInfoType: IdeInfoType = IdeInfoType.RUSTROVER
  override val ideInfo: IdeInfo = DefaultRustRover
}
