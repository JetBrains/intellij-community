package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vfs.VirtualFile;
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
    //noinspection unchecked
    List<VirtualFile> unversionedFiles = (List<VirtualFile>) e.getDataContext().getData(ChangesListView.UNVERSIONED_FILES_KEY);
    e.getPresentation().setEnabled(project != null &&
                                   (changes != null && changes.length > 0) || (unversionedFiles != null && unversionedFiles.size() > 0));
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
    if (changes == null) return;
    //noinspection unchecked
    List<VirtualFile> unversionedFiles = (List<VirtualFile>) e.getDataContext().getData(ChangesListView.UNVERSIONED_FILES_KEY);

    askAndMove(project, changes, unversionedFiles);
  }

  public static void askAndMove(final Project project, final Change[] changes, final List<VirtualFile> unversionedFiles) {
    final ChangeListManagerImpl listManager = ChangeListManagerImpl.getInstanceImpl(project);
    final List<LocalChangeList> lists = listManager.getChangeLists();
    ChangeListChooser chooser = new ChangeListChooser(project, getPreferredLists(lists, changes, true), guessPreferredList(lists, changes));
    chooser.show();
    LocalChangeList resultList = chooser.getSelectedList();
    if (resultList != null) {
      listManager.moveChangesTo(resultList, changes);
      listManager.addUnversionedFiles(resultList, unversionedFiles);
    }
  }

  private static ChangeList guessPreferredList(final List<LocalChangeList> lists, final Change[] changes) {
    List<LocalChangeList> preferredLists = getPreferredLists(lists, changes, false);

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

  private static List<LocalChangeList> getPreferredLists(final List<LocalChangeList> lists,
                                                    final Change[] changes,
                                                    final boolean includeDefaultIfEmpty) {
    List<LocalChangeList> preferredLists = new ArrayList<LocalChangeList>(lists);
    Set<Change> changesAsSet = new THashSet<Change>(Arrays.asList(changes));
    for (LocalChangeList list : lists) {
      for (Change change : list.getChanges()) {
        if (changesAsSet.contains(change)) {
          preferredLists.remove(list);
          break;
        }
      }
    }

    if (preferredLists.isEmpty() && includeDefaultIfEmpty) {
      for (LocalChangeList list : lists) {
        if (list.isDefault()) {
          preferredLists.add(list);
        }
      }
    }

    return preferredLists;
  }
}
