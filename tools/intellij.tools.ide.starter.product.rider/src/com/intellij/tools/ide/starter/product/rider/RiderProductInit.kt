// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.product.rider

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.IdeProductInit
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * Rider [IdeInfo] resolved from DI.
 *
 * Tests that need Rider should depend on this module `intellij.tools.ide.starter.product.rider`
 */
val IdeInfo.Companion.Rider: IdeInfo
  get() {
    return di.direct.instance<IdeInfo>(tag = IdeInfoType.RIDER)
  }

internal val DefaultRider = IdeInfo(
  productCode = "RD",
  platformPrefix = "Rider",
  executableFileName = "rider",
  fullName = "Rider",
  qodanaProductCode = "QDNET"
)

/**
 * Registers Rider [IdeInfo] in DI and initializes Dev Build Server support.
 */
class RiderProductInit : IdeProductInit {
  override val ideInfoType: IdeInfoType = IdeInfoType.RIDER
  override val ideInfo: IdeInfo = DefaultRider
}
