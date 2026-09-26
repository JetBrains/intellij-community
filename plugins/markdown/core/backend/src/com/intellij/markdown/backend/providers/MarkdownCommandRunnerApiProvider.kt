// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.providers

import com.intellij.markdown.backend.services.MarkdownCommandRunnerRemoteApiImpl
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import org.intellij.plugins.markdown.service.MarkdownCommandRunnerRemoteApi

internal class MarkdownCommandRunnerApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<MarkdownCommandRunnerRemoteApi>()) {
      MarkdownCommandRunnerRemoteApiImpl()
    }
  }
}
