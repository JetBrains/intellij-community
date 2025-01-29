// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run

import com.intellij.debugger.engine.DelayedRemoteConnection
import com.intellij.execution.configurations.RemoteConnection

class MavenRemoteConnection(useSockets: Boolean, hostName: String, address: String, serverMode: Boolean, val enhanceMavenOpts: (String) -> String) : RemoteConnection(useSockets, hostName, address, serverMode), DelayedRemoteConnection {
  override var attachRunnable: Runnable? = null
}