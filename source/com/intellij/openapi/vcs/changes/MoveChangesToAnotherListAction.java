package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindow;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

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

    if (project != null && changes == null && unversionedFiles == null) {
      unversionedFiles = new ArrayList<VirtualFile>();
      changes = getChangesForSelectedFiles(project, changes, unversionedFiles, null, e);
    }

    e.getPresentation().setEnabled(project != null &&
                                   (changes != null && changes.length > 0) || (unversionedFiles != null && unversionedFiles.size() > 0));
  }

  @Nullable
  private static Change[] getChangesForSelectedFiles(final Project project, Change[] changes, final List<VirtualFile> unversionedFiles,
                                                     @Nullable final List<VirtualFile> changedFiles, final AnActionEvent e) {
    if (ProjectLevelVcsManager.getInstance(project).getAllActiveVcss().length == 0) {
      return null;
    }
    
    VirtualFile[] virtualFiles = (VirtualFile[])e.getDataContext().getData(DataConstants.VIRTUAL_FILE_ARRAY);
    if (virtualFiles != null) {
      List<Change> changesInFiles = new ArrayList<Change>();
      for(VirtualFile vFile: virtualFiles) {
        if (ChangeListManager.getInstance(project).getStatus(vFile).equals(FileStatus.UNKNOWN)) {
          unversionedFiles.add(vFile);
          if (changedFiles != null) changedFiles.add(vFile);
        }
        else {
          Change change = ChangeListManager.getInstance(project).getChange(vFile);
          if (change != null) {
            changesInFiles.add(change);
            if (changedFiles != null) changedFiles.add(vFile);
          }
        }
      }
      if (changesInFiles.size() > 0 || unversionedFiles.size() > 0) {
        changes = changesInFiles.toArray(new Change[changesInFiles.size()]);
      }
    }
    return changes;
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
    //noinspection unchecked
    List<VirtualFile> unversionedFiles = (List<VirtualFile>) e.getDataContext().getData(ChangesListView.UNVERSIONED_FILES_KEY);

    final List<VirtualFile> changedFiles = new ArrayList<VirtualFile>();
    boolean activateChangesView = false;
    if (project != null && changes == null && unversionedFiles == null) {
      unversionedFiles = new ArrayList<VirtualFile>();
      changes = getChangesForSelectedFiles(project, changes, unversionedFiles, changedFiles, e);
      activateChangesView = true;
    }

    if (changes == null) return;

    if (!askAndMove(project, changes, unversionedFiles)) return;
    if (activateChangesView) {
      ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewManager.TOOLWINDOW_ID);
      if (!window.isVisible()) {
        window.activate(new Runnable() {
          public void run() {
            if (changedFiles.size() > 0) {
              ChangesViewManager.getInstance(project).selectFile(changedFiles.get(0));
            }
          }
        });
      }
    }
  }

  public static boolean askAndMove(final Project project, final Change[] changes, final List<VirtualFile> unversionedFiles) {
    final ChangeListManagerImpl listManager = ChangeListManagerImpl.getInstanceImpl(project);
    final List<LocalChangeList> lists = listManager.getChangeLists();
    ChangeListChooser chooser = new ChangeListChooser(project, getPreferredLists(lists, changes, true), guessPreferredList(lists, changes));
    chooser.show();
    LocalChangeList resultList = chooser.getSelectedList();
    if (resultList != null) {
      listManager.moveChangesTo(resultList, changes);
      if (unversionedFiles != null) {
        listManager.addUnversionedFiles(resultList, unversionedFiles);
      }
      return true;
    }
    return false;
  }

  @Nullable
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
