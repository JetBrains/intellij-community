// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.product.aqua

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.IdeProductInit
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * Aqua [IdeInfo] resolved from DI.
 *
 * Tests that need Aqua should depend on this module `intellij.tools.ide.starter.product.aqua`
 */
val IdeInfo.Companion.Aqua: IdeInfo
  get() {
    return di.direct.instance<IdeInfo>(tag = IdeInfoType.AQUA)
  }

internal val DefaultAqua = IdeInfo(
  productCode = "QA",
  platformPrefix = "Aqua",
  executableFileName = "aqua",
  fullName = "Aqua"
)

/**
 * Registers Aqua [IdeInfo] in DI and initializes Dev Build Server support.
 */
class AquaProductInit : IdeProductInit {
  override val ideInfoType: IdeInfoType = IdeInfoType.AQUA
  override val ideInfo: IdeInfo = DefaultAqua
}
