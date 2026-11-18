// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.utils

import com.intellij.ide.starter.di.di
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * Maps a public download url of the Starter to the mirror of the test environment.
 * The default instance keeps each url. A test environment binds its own instance in the DI container.
 */
interface DownloadMirror {

  /** Returns the url to download [url] from. */
  fun mirror(url: String): String = url

  /** Tells whether a 403 answer for [url] means a missing file. A mirror can answer 403 when it hides its file listing. */
  fun isMissingOn403(url: String): Boolean = false

  companion object {
    val instance: DownloadMirror
      get() = di.direct.instance<DownloadMirror>()
  }
}
