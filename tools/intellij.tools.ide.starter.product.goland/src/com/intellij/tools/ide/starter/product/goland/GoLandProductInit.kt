// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.product.goland

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.IdeProductInit
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * GoLand [IdeInfo] resolved from DI.
 *
 * Tests that need GoLand should depend on this module `intellij.tools.ide.starter.product.goland`
 */
val IdeInfo.Companion.GoLand: IdeInfo
  get() {
    return di.direct.instance<IdeInfo>(tag = IdeInfoType.GOLAND)
  }

internal val DefaultGoLand = IdeInfo(
  productCode = "GO",
  platformPrefix = "GoLand",
  executableFileName = "goland",
  fullName = "GoLand",
  qodanaProductCode = "QDGO"
)

/**
 * Registers GoLand [IdeInfo] in DI.
 */
class GoLandProductInit : IdeProductInit {
  override val ideInfoType: IdeInfoType = IdeInfoType.GOLAND
  override val ideInfo: IdeInfo = DefaultGoLand
}
