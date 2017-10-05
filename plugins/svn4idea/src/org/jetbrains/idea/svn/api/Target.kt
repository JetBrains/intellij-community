/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.api

import org.tmatesoft.svn.core.SVNURL
import java.io.File

class Target private constructor(val url: SVNURL?, val file: File?, pegRevision: Revision?) {
  val pegRevision = pegRevision ?: Revision.UNDEFINED

  val path: String get() = if (isFile()) file!!.path else url!!.toString()
  fun isFile() = file != null
  fun isUrl() = url != null

  override fun toString() = path + '@' + pegRevision

  companion object {
    @JvmStatic
    @JvmOverloads
    fun on(url: SVNURL, pegRevision: Revision? = null) = Target(url, null, pegRevision)

    @JvmStatic
    @JvmOverloads
    fun on(file: File, pegRevision: Revision? = null) = Target(null, file, pegRevision)
  }
}
