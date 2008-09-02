package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.idea.svn.actions.ChangeSetMergerFactory;

import java.util.ArrayList;
import java.util.List;

public class SelectedChangeSetChecker extends SelectedChangeListsChecker {
  private final List<Change> mySelectedChanges;

  public SelectedChangeSetChecker() {
    super(null);
    mySelectedChanges = new ArrayList<Change>();
  }

  private void fillChanges(final AnActionEvent event) {
    mySelectedChanges.clear();

    final Change[] changes = event.getData(VcsDataKeys.CHANGES);
    if (changes != null) {
      // bugfix: event.getData(VcsDataKeys.CHANGES) return list with duplicates.
      // so the check is added here
      for (Change change : changes) {
        if (! mySelectedChanges.contains(change)) {
          mySelectedChanges.add(change);
        }
      }
    }
  }

  public void execute(final AnActionEvent event) {
    super.execute(event);
    fillChanges(event);
  }

  public boolean isValid() {
    return super.isValid() && (myChangeListsList.size() == 1) && (! mySelectedChanges.isEmpty());
  }

  public MergerFactory createFactory() {
    return new ChangeSetMergerFactory(myChangeListsList.get(0), mySelectedChanges);
  }

  public List<CommittedChangeList> getSelectedLists() {
    return null;
  }
}
