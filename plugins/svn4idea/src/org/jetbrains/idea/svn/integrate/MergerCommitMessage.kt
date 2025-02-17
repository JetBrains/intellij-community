// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.integrate

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.svn.SvnBundle

interface MergerCommitMessage {
  fun getCommitMessage(project: Project, currentMessage: @Nls String, branchName: String, changeLists: List<CommittedChangeList>): @Nls String

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<MergerCommitMessage> = ExtensionPointName("com.intellij.vcs.svn.mergerCommitMessage")
  }
}

class DefaultMergerCommitMessage : MergerCommitMessage {
  override fun getCommitMessage(project: Project, @Nls currentMessage: String, branchName: String, changeLists: List<CommittedChangeList>): @Nls String {
    val messageBuilder: @Nls StringBuilder =
      StringBuilder(StringUtil.defaultIfEmpty(currentMessage, SvnBundle.message("label.merged.from.branch", branchName)))

    for (list in changeLists) {
      messageBuilder.append('\n')
      messageBuilder.append(SvnBundle.message("merge.chunk.changelist.description", list.getComment().trim { it <= ' ' }, list.getNumber()))
    }

    return messageBuilder.toString()
  }
}