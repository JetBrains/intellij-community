// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.internal

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Service(Service.Level.APP)
class MavenPluginDisposable : Disposable {

  @Volatile
  var disposed: Boolean = false

  companion object {
    @JvmStatic
    fun getInstance(): MavenPluginDisposable = ApplicationManager.getApplication()
      .getService(MavenPluginDisposable::class.java)
  }

  override fun dispose() {
    disposed = true
  }
}
