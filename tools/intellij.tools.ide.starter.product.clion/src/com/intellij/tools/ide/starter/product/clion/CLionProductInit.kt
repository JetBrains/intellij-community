// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.product.clion

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.IdeProductInit
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * CLion [IdeInfo] resolved from DI.
 *
 * Tests that need CLion should depend on this module `intellij.tools.ide.starter.product.clion`
 */
val IdeInfo.Companion.CLion: IdeInfo
  get() {
    return di.direct.instance<IdeInfo>(tag = IdeInfoType.CLION)
  }

internal val DefaultCLion = IdeInfo(
  productCode = "CL",
  platformPrefix = "CLion",
  executableFileName = "clion",
  fullName = "CLion",
  qodanaProductCode = "QDCPP"
)

/**
 * Registers CLion [IdeInfo] in DI.
 */
class CLionProductInit : IdeProductInit {
  override val ideInfoType: IdeInfoType = IdeInfoType.CLION
  override val ideInfo: IdeInfo = DefaultCLion
}
