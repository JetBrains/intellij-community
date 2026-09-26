// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.product.gateway

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.models.IdeProductInit
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * Gateway [IdeInfo] resolved from DI.
 *
 * Tests that need Gateway should depend on this module `intellij.tools.ide.starter.product.gateway`
 */
val IdeInfo.Companion.Gateway: IdeInfo
  get() {
    return di.direct.instance<IdeInfo>(tag = IdeInfoType.GATEWAY)
  }

internal val DefaultGateway = IdeInfo(
  productCode = "GW",
  platformPrefix = "Gateway",
  executableFileName = "gateway",
  fullName = "Gateway"
)

/**
 * Registers Gateway [IdeInfo] in DI.
 */
class GatewayProductInit : IdeProductInit {
  override val ideInfoType: IdeInfoType = IdeInfoType.GATEWAY
  override val ideInfo: IdeInfo = DefaultGateway
}
