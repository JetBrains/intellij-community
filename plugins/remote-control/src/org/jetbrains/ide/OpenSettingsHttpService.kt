package org.jetbrains.ide

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder

internal class OpenSettingsService : RestService() {
  override fun getServiceName() = "settings"

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val name = urlDecoder.parameters()["name"]?.firstOrNull()?.trim() ?: return parameterMissedErrorMessage("name")
    if (!OpenSettingsJbProtocolService.Util.doOpenSettings(name)) {
      return "no configurables found"
    }

    sendOk(request, context)
    return null
  }
}
