package org.jetbrains.settingsRepository.git

import com.intellij.openapi.application.ex.ApplicationInfoEx
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.merge.MergeMessageFormatter
import java.net.InetAddress

public interface CommitMessageFormatter {
  public fun message(text: String): String = text

  public fun prependMessage(builder: StringBuilder = StringBuilder()): StringBuilder = builder

  public fun mergeMessage(refsToMerge: List<Ref>, target: Ref): String = MergeMessageFormatter().format(refsToMerge, target)
}

class IdeaCommitMessageFormatter : CommitMessageFormatter {
  override fun message(text: String) = StringBuilder().appendCommitOwnerInfo().append(text).toString()

  override fun prependMessage(builder: StringBuilder) = builder.appendCommitOwnerInfo()

  override fun mergeMessage(refsToMerge: List<Ref>, target: Ref) = StringBuilder().appendCommitOwnerInfo().append(super.mergeMessage(refsToMerge, target)).toString()

  fun StringBuilder.appendCommitOwnerInfo(): StringBuilder {
    appendAppName()
    append(' ').append('<').append(System.getProperty("user.name", "unknown-user")).append('@').append(InetAddress.getLocalHost().getHostName())
    append(' ')
    return this
  }

  fun StringBuilder.appendAppName() {
    val appInfo = ApplicationInfoEx.getInstanceEx()
    if (appInfo != null) {
      val build = appInfo.getBuild()
      append(build.getProductCode()).append('-')
      if (appInfo.getMajorVersion() != null && !appInfo.isEAP()) {
        append(appInfo.getFullVersion())
      }
      else {
        var buildString = build.asStringWithoutProductCode()
        if (build.getBuildNumber() == Integer.MAX_VALUE) {
          buildString = buildString.replace(".SNAPSHOT", "")
        }
        append(buildString)
      }
    }
  }
}