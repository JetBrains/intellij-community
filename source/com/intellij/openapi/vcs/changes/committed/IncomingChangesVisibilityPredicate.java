package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CachingCommittedChangesProvider;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class IncomingChangesVisibilityPredicate implements NotNullFunction<Project, Boolean> {
  @NotNull
  public Boolean fun(final Project project) {
    final AbstractVcs[] abstractVcses = ProjectLevelVcsManager.getInstance(project).getAllActiveVcss();
    for(AbstractVcs vcs: abstractVcses) {
      if (vcs.getCommittedChangesProvider() instanceof CachingCommittedChangesProvider) {
        return Boolean.TRUE;
      }
    }
    return Boolean.FALSE;
  }
}
