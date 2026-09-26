// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.product.datagrip

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.IdeProductInit
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * DataGrip [IdeInfo] resolved from DI.
 *
 * Tests that need DataGrip should depend on this module `intellij.tools.ide.starter.product.datagrip`
 */
val IdeInfo.Companion.DataGrip: IdeInfo
  get() {
    return di.direct.instance<IdeInfo>(tag = IdeInfoType.DATAGRIP)
  }

internal val DefaultDataGrip = IdeInfo(
  productCode = "DB",
  platformPrefix = "DataGrip",
  executableFileName = "datagrip",
  fullName = "DataGrip"
)

/**
 * Registers DataGrip [IdeInfo] in DI.
 */
class DataGripProductInit : IdeProductInit {
  override val ideInfoType: IdeInfoType = IdeInfoType.DATAGRIP
  override val ideInfo: IdeInfo = DefaultDataGrip
}
