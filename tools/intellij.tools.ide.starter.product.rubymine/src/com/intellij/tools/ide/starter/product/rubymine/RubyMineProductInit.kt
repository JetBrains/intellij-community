// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.product.rubymine

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.IdeProductInit
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * RubyMine [IdeInfo] resolved from DI.
 *
 * Tests that need RubyMine should depend on this module `intellij.tools.ide.starter.product.rubymine`
 */
val IdeInfo.Companion.RubyMine: IdeInfo
  get() {
    return di.direct.instance<IdeInfo>(tag = IdeInfoType.RUBYMINE)
  }

internal val DefaultRubyMine = IdeInfo(
  productCode = "RM",
  platformPrefix = "Ruby",
  executableFileName = "rubymine",
  fullName = "RubyMine"
)

/**
 * Registers RubyMine [IdeInfo] in DI and initializes Dev Build Server support.
 */
class RubyMineProductInit : IdeProductInit {
  override val ideInfoType: IdeInfoType = IdeInfoType.RUBYMINE
  override val ideInfo: IdeInfo = DefaultRubyMine
}
