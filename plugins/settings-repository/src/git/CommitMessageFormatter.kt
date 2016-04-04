/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.settingsRepository.git

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.merge.MergeMessageFormatter
import java.net.InetAddress

interface CommitMessageFormatter {
  fun message(text: String): String = text

  fun prependMessage(builder: StringBuilder = StringBuilder()): StringBuilder = builder

  fun mergeMessage(refsToMerge: List<Ref>, target: Ref): String = MergeMessageFormatter().format(refsToMerge, target)
}

class IdeaCommitMessageFormatter : CommitMessageFormatter {
  override fun message(text: String) = StringBuilder().appendCommitOwnerInfo().append(text).toString()

  override fun prependMessage(builder: StringBuilder) = builder.appendCommitOwnerInfo()

  override fun mergeMessage(refsToMerge: List<Ref>, target: Ref) = StringBuilder().appendCommitOwnerInfo().append(super.mergeMessage(refsToMerge, target)).toString()

  fun StringBuilder.appendCommitOwnerInfo(avoidAppInfoInstantiation: Boolean = false): StringBuilder {
    if (avoidAppInfoInstantiation) {
      append(ApplicationNamesInfo.getInstance().productName)
    }
    else {
      appendAppName()
    }
    append(' ').append('<').append(System.getProperty("user.name", "unknown-user")).append('@').append(InetAddress.getLocalHost().hostName)
    append(' ')
    return this
  }

  fun StringBuilder.appendAppName() {
    val appInfo = ApplicationInfoEx.getInstanceEx()
    if (appInfo != null) {
      val build = appInfo.build
      append(build.productCode).append('-')
      if (appInfo.majorVersion != null && !appInfo.isEAP) {
        append(appInfo.fullVersion)
      }
      else {
        var buildString = build.asStringWithoutProductCode()
        if (build.buildNumber == Integer.MAX_VALUE) {
          buildString = buildString.replace(".SNAPSHOT", "")
        }
        append(buildString)
      }
    }
  }
}