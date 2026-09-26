// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.product.android.studio

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.IdeProductInit
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * Android Studio [IdeInfo] resolved from DI.
 *
 * Tests that need Android Studio should depend on this module `intellij.tools.ide.starter.product.android.studio`
 */
val IdeInfo.Companion.AndroidStudio: IdeInfo
  get() {
    return di.direct.instance<IdeInfo>(tag = IdeInfoType.ANDROID_STUDIO)
  }

internal val DefaultAndroidStudio = IdeInfo(
  productCode = "AI",
  platformPrefix = "AndroidStudio",
  executableFileName = "studio",
  fullName = "Android Studio"
)

/**
 * Registers Android Studio [IdeInfo] in DI.
 */
class AndroidStudioProductInit : IdeProductInit {
  override val ideInfoType: IdeInfoType = IdeInfoType.ANDROID_STUDIO
  override val ideInfo: IdeInfo = DefaultAndroidStudio
}
