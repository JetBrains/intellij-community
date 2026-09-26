package com.intellij.terminal.backend.rpc

import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.terminal.backend.hyperlinks.rpc.TerminalHyperlinksRemoteApiImpl
import com.intellij.terminal.backend.hyperlinks.rpc.TerminalHyperlinksSessionRemoteApiImpl
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksRemoteApi
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksSessionRemoteApi
import org.jetbrains.plugins.terminal.settings.impl.TerminalProjectOptionsRemoteApi
import org.jetbrains.plugins.terminal.settings.impl.TerminalTabsStorageRemoteApi
import org.jetbrains.plugins.terminal.startup.TerminalExecOptionsCustomizationRemoteApi

internal class TerminalRemoteApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<TerminalProjectOptionsRemoteApi>()) { TerminalProjectOptionsRemoteApiImpl() }
    remoteApi(remoteApiDescriptor<TerminalTabsStorageRemoteApi>()) { TerminalTabsStorageRemoteApiImpl() }
    remoteApi(remoteApiDescriptor<TerminalHyperlinksRemoteApi>()) { TerminalHyperlinksRemoteApiImpl() }
    remoteApi(remoteApiDescriptor<TerminalHyperlinksSessionRemoteApi>()) { TerminalHyperlinksSessionRemoteApiImpl() }
    remoteApi(remoteApiDescriptor<TerminalExecOptionsCustomizationRemoteApi>()) { TerminalExecOptionsCustomizationRemoteApiImpl() }
  }
}