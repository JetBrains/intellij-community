package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AlienChangeListBrowser extends ChangesBrowser implements ChangesBrowserExtender {
  private final AbstractVcs myVcs;

  public AlienChangeListBrowser(final Project project, final List<? extends ChangeList> changeLists, final List<Change> changes,
                                final ChangeList initialListSelection, final boolean capableOfExcludingChanges,
                                final boolean highlightProblems, final AbstractVcs vcs) {
    super(project, changeLists, changes, initialListSelection, capableOfExcludingChanges, highlightProblems);
    myVcs = vcs;
  }

  protected void setInitialSelection(final List<? extends ChangeList> changeLists, final List<Change> changes, final ChangeList initialListSelection) {
    if (! changeLists.isEmpty()) {
      mySelectedChangeList = changeLists.get(0);
    }
  }

  public void addToolbarActions(final DialogWrapper dialogWrapper) {
  }

  public void addSelectedListChangeListener(final SelectedListChangeListener listener) {
    // does nothing - only one change list so far
  }

  public List<AbstractVcs> getAffectedVcses() {
    return Collections.singletonList(myVcs);
  }

  public List<Change> getCurrentIncludedChanges() {
    return new ArrayList<Change>(myViewer.getIncludedChanges());
  }
}
