// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.product.idea.ultimate

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.IdeProductInit
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * IntelliJ IDEA Ultimate [IdeInfo] resolved from DI.
 *
 * Tests that need IntelliJ IDEA Ultimate should depend on this module `intellij.tools.ide.starter.product.idea.ultimate`
 */
val IdeInfo.Companion.IdeaUltimate: IdeInfo
  get() {
    return di.direct.instance<IdeInfo>(tag = IdeInfoType.IDEA_ULTIMATE)
  }

internal val DefaultIdeaUltimate = IdeInfo(
  productCode = "IU",
  platformPrefix = "idea",
  executableFileName = "idea",
  fullName = "IDEA",
  qodanaProductCode = "QDJVM"
)

/**
 * Registers IntelliJ IDEA Ultimate [IdeInfo] in DI.
 */
class IdeaUltimateProductInit : IdeProductInit {
  override val ideInfoType: IdeInfoType = IdeInfoType.IDEA_ULTIMATE
  override val ideInfo: IdeInfo = DefaultIdeaUltimate
}
