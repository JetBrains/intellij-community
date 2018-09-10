// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.statistics

import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.idea.svn.NestedCopyType
import org.jetbrains.idea.svn.SvnVcs

class SvnWorkingCopyFormatUsagesCollector : ProjectUsagesCollector() {
  override fun getGroupId() = "statistics.vcs.svn.format"

  override fun getUsages(project: Project): Set<UsageDescriptor> {
    val vcs = SvnVcs.getInstance(project)

    // do not track roots with errors (SvnFileUrlMapping.getErrorRoots()) as they are "not usable" until errors are resolved
    // skip externals and switched directories as they will have the same format
    val roots = vcs.svnFileUrlMapping.allWcInfos.filter { it.type == null || it.type == NestedCopyType.inner }

    return roots.groupingBy { it.format }.eachCount().map { UsageDescriptor(it.key.toString(), it.value) }.toSet()
  }
}
