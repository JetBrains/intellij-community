// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.product.pycharm.community

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.IdeProductInit
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * PyCharm Community [IdeInfo] resolved from DI.
 *
 * Tests that need PyCharm Community should depend on this module `intellij.tools.ide.starter.product.pycharm.community`
 */
val IdeInfo.Companion.PyCharmCommunity: IdeInfo
  get() {
    return di.direct.instance<IdeInfo>(tag = IdeInfoType.PYCHARM_COMMUNITY)
  }

internal val DefaultPyCharmCommunity = IdeInfo(
  productCode = "PC",
  platformPrefix = "PyCharmCore",
  executableFileName = "pycharm",
  fullName = "PyCharm",
  qodanaProductCode = "QDPYC"
)

/**
 * Registers PyCharm Community [IdeInfo] in DI and initializes Dev Build Server support.
 */
class PyCharmCommunityProductInit : IdeProductInit {
  override val ideInfoType: IdeInfoType = IdeInfoType.PYCHARM_COMMUNITY
  override val ideInfo: IdeInfo = DefaultPyCharmCommunity
}
