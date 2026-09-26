// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.product.pycharm

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.IdeProductInit
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * PyCharm Professional [IdeInfo] resolved from DI.
 *
 * Tests that need PyCharm Professional should depend on this module `intellij.tools.ide.starter.product.pycharm`
 */
val IdeInfo.Companion.PyCharm: IdeInfo
  get() {
    return di.direct.instance<IdeInfo>(tag = IdeInfoType.PYCHARM)
  }

internal val DefaultPyCharm = IdeInfo(
  productCode = "PY",
  platformPrefix = "Python",
  executableFileName = "pycharm",
  fullName = "PyCharm",
  qodanaProductCode = "QDPY"
)

/**
 * Registers PyCharm Professional [IdeInfo] in DI and initializes Dev Build Server support.
 */
class PyCharmProductInit : IdeProductInit {
  override val ideInfoType: IdeInfoType = IdeInfoType.PYCHARM
  override val ideInfo: IdeInfo = DefaultPyCharm
}
