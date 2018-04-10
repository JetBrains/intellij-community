// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.api

import java.io.File

class Target private constructor(val url: Url?, val file: File?, pegRevision: Revision?) {
  val pegRevision = pegRevision ?: Revision.UNDEFINED

  val path: String get() = if (isFile()) file!!.path else url!!.toString()
  fun isFile() = file != null
  fun isUrl() = url != null

  override fun toString() = path + '@' + pegRevision

  companion object {
    @JvmStatic
    @JvmOverloads
    fun on(url: Url, pegRevision: Revision? = null) = Target(url, null, pegRevision)

    @JvmStatic
    @JvmOverloads
    fun on(file: File, pegRevision: Revision? = null) = Target(null, file, pegRevision)
  }
}
