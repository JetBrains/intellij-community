package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import gnu.trove.THashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public class MoveChangesToAnotherListAction extends AnAction {
  public MoveChangesToAnotherListAction() {
    super(VcsBundle.message("move.to.another.list.action.text"), VcsBundle.message("move.to.another.list.action.description"),
          IconLoader.getIcon("/actions/fileStatus.png"));
  }

  public void update(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
    e.getPresentation().setEnabled(project != null && changes != null && changes.length > 0);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
    if (changes == null) return;

    askAndMove(project, changes);
  }

  public static void askAndMove(final Project project, final Change[] changes) {
    final ChangeListManager listManager = ChangeListManager.getInstance(project);
    final List<ChangeList> lists = listManager.getChangeLists();
    ChangeListChooser chooser = new ChangeListChooser(project, getPreferredLists(lists, changes), guessPreferredList(lists, changes));
    chooser.show();
    ChangeList resultList = chooser.getSelectedList();
    if (resultList != null) {
      listManager.moveChangesTo(resultList, changes);
    }
  }

  private static ChangeList guessPreferredList(final List<ChangeList> lists, final Change[] changes) {
    List<ChangeList> preferredLists = getPreferredLists(lists, changes);

    for (ChangeList preferredList : preferredLists) {
      if (preferredList.getChanges().isEmpty()) {
        return preferredList;
      }
    }

    if (preferredLists.size() > 0) {
      return preferredLists.get(0);
    }

    return null;
  }

  private static List<ChangeList> getPreferredLists(final List<ChangeList> lists, final Change[] changes) {
    List<ChangeList> preferredLists = new ArrayList<ChangeList>(lists);
    Set<Change> changesAsSet = new THashSet<Change>(Arrays.asList(changes));
    for (ChangeList list : lists) {
      for (Change change : list.getChanges()) {
        if (changesAsSet.contains(change)) {
          preferredLists.remove(list);
          break;
        }
      }
    }
    return preferredLists;
  }
}
