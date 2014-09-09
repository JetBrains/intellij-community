package org.jetbrains.plugins.settingsRepository.git

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.settingsRepository.LOG
import org.eclipse.jgit.util.io.NullOutputStream
import org.eclipse.jgit.diff.DiffFormatter

fun reset(manager: GitRepositoryManager, indicator: ProgressIndicator) {
  LOG.debug("Reset to theirs")

  // https://github.com/centic9/jgit-cookbook/blob/master/src/main/java/org/dstadler/jgit/porcelain/ShowBranchDiff.java
  val diffFormatter = DiffFormatter(NullOutputStream.INSTANCE)
}
