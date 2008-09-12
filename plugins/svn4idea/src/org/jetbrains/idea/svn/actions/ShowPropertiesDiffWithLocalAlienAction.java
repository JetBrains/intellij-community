package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import org.jetbrains.idea.svn.SvnRevisionNumber;

public class ShowPropertiesDiffWithLocalAlienAction extends ShowPropertiesDiffWithLocalAction {
  @Override
  protected boolean checkVcs(final Project project, final Change change) {
    final ContentRevision contentRevision = change.getBeforeRevision();
    return (contentRevision != null) && (contentRevision.getRevisionNumber() instanceof SvnRevisionNumber);
  }
}
