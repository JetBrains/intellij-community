// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.product.jetbrains.light

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.IdeProductInit
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * JetBrains Light [IdeInfo] resolved from DI.
 *
 * JetBrains Light is the standalone light IDE. It opens a directory as a light project, with no project import and no indexing.
 * Tests that need JetBrains Light should depend on this module `intellij.tools.ide.starter.product.jetbrains.light`.
 */
val IdeInfo.Companion.JetBrainsLight: IdeInfo
  get() {
    return di.direct.instance<IdeInfo>(tag = IdeInfoType.JETBRAINS_LIGHT)
  }

/**
 * The platform prefix is the `Light` key of `build/dev-build.json`; the executable name is the `script` name of its application info.
 *
 * JetBrains Light does not bundle the performance-testing plugin, which carries the driver and the command runner of the starter.
 * A dev build therefore adds it as an additional module, the way the Gateway dev build does.
 */
internal val DefaultJetBrainsLight = IdeInfo(
  productCode = "JB",
  platformPrefix = "Light",
  executableFileName = "jetbrains",
  fullName = "JetBrains Light",
  additionalModules = listOf("intellij.performanceTesting"),
)

/**
 * Registers JetBrains Light [IdeInfo] in DI.
 */
class JetBrainsLightProductInit : IdeProductInit {
  override val ideInfoType: IdeInfoType = IdeInfoType.JETBRAINS_LIGHT
  override val ideInfo: IdeInfo = DefaultJetBrainsLight
}
