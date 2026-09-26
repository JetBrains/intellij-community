// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.product.idea.community

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.IdeProductInit
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * IntelliJ IDEA Community [IdeInfo] resolved from DI.
 *
 * Tests that need IntelliJ IDEA Community should depend on this module `intellij.tools.ide.starter.product.idea.community`
 */
val IdeInfo.Companion.IdeaCommunity: IdeInfo
  get() {
    return di.direct.instance<IdeInfo>(tag = IdeInfoType.IDEA_COMMUNITY)
  }

internal val DefaultIdeaCommunity = IdeInfo(
  productCode = "IC",
  platformPrefix = "Idea",
  executableFileName = "idea",
  fullName = "IDEA Community",
  qodanaProductCode = "QDJVMC"
)

/**
 * Registers IntelliJ IDEA Community [IdeInfo] in DI.
 */
class IdeaCommunityProductInit : IdeProductInit {
  override val ideInfoType: IdeInfoType = IdeInfoType.IDEA_COMMUNITY
  override val ideInfo: IdeInfo = DefaultIdeaCommunity
}
