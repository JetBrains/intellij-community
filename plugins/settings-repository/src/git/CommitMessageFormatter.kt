// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.git

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.util.NlsSafe
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.merge.MergeMessageFormatter
import org.jetbrains.settingsRepository.icsManager
import java.net.InetAddress

interface CommitMessageFormatter {
  @NlsSafe
  fun message(@NlsSafe text: String): String = text

  fun prependMessage(builder: StringBuilder = StringBuilder()): StringBuilder = builder

  fun mergeMessage(refsToMerge: List<Ref>, target: Ref): String = MergeMessageFormatter().format(refsToMerge, target)
}

class IdeaCommitMessageFormatter : CommitMessageFormatter {
  override fun message(text: String) = appendCommitOwnerInfo().append(text).toString()

  override fun prependMessage(builder: StringBuilder) = appendCommitOwnerInfo(builder = builder)

  override fun mergeMessage(refsToMerge: List<Ref>, target: Ref) = appendCommitOwnerInfo().append(super.mergeMessage(refsToMerge, target)).toString()

  fun appendCommitOwnerInfo(avoidAppInfoInstantiation: Boolean = false, builder: StringBuilder = StringBuilder()): StringBuilder {
    if (avoidAppInfoInstantiation) {
      builder.append(ApplicationNamesInfo.getInstance().productName)
    }
    else {
      builder.appendAppName()
    }

    if (!ApplicationManager.getApplication()!!.isUnitTestMode && icsManager.settings.includeHostIntoCommitMessage) {
      builder.append(' ').append('<').append(System.getProperty("user.name", "unknown-user"))
      builder.append('@').append(InetAddress.getLocalHost().hostName)
    }
    builder.append(' ')
    return builder
  }

  private fun StringBuilder.appendAppName() {
    val appInfo = ApplicationInfoEx.getInstanceEx()
    if (appInfo != null) {
      val build = appInfo.build
      append(build.productCode).append('-')
      if (appInfo.majorVersion != null && !appInfo.isEAP) {
        append(appInfo.fullVersion)
      }
      else {
        append(build.asStringWithoutProductCodeAndSnapshot())
      }
    }
  }
}