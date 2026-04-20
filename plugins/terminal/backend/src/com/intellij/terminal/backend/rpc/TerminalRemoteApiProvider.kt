package com.intellij.terminal.backend.rpc

import com.intellij.platform.rpc.backend.RemoteApiProvider

internal class TerminalRemoteApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    // todo: add new RPC implementations for backend-dependent terminal features
  }
}