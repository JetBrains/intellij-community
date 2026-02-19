package com.intellij.terminal.backend.rpc

import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalSessionApi
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalShellsDetectorApi
import org.jetbrains.plugins.terminal.block.reworked.session.rpc.TerminalTabsManagerApi

internal class TerminalRemoteApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<TerminalTabsManagerApi>()) {
      TerminalTabsManagerApiImpl()
    }
    remoteApi(remoteApiDescriptor<TerminalSessionApi>()) {
      TerminalSessionApiImpl()
    }
    remoteApi(remoteApiDescriptor<TerminalShellsDetectorApi>()) {
      TerminalShellsDetectorApiImpl()
    }
  }
}