package com.intellij.terminal.backend.rpc

import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.plugins.terminal.settings.impl.TerminalProjectOptionsRemoteApi

internal class TerminalRemoteApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<TerminalProjectOptionsRemoteApi>()) { TerminalProjectOptionsRemoteApiImpl() }
  }
}