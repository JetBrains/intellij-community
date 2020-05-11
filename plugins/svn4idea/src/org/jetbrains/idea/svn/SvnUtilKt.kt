// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn

import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.idea.svn.SvnUtil.getWcDb
import java.util.stream.Collectors.toList

internal fun putWcDbFilesToVfs(infos: Collection<RootUrlInfo>) {
  if (!SvnVcs.ourListenToWcDb) return

  val wcDbFiles = infos.stream()
    .filter { it.format.isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN) }
    .filter { NestedCopyType.switched != it.type }
    .map { it.ioFile }
    .map { getWcDb(it) }
    .collect(toList())

  LocalFileSystem.getInstance().refreshIoFiles(wcDbFiles, true, false, null)
}
