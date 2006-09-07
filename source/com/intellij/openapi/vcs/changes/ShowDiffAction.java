package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.CommonBundle;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class ShowDiffAction extends AnAction {
  public ShowDiffAction() {
    super(VcsBundle.message("show.diff.action.text"), VcsBundle.message("show.diff.action.description"), IconLoader.getIcon("/actions/diff.png"));
  }

  public void update(AnActionEvent e) {
    Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    e.getPresentation().setEnabled(project != null && canShowDiff(changes));
  }

  private static boolean canShowDiff(Change[] changes) {
    if (changes == null || changes.length == 0) return false;
    return !ChangesUtil.getFilePath(changes [0]).isDirectory();
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
    if (project == null || changes == null) return;

    int index = 0;
    if (changes.length == 1) {
      final Change selectedChange = changes[0];
      ChangeList changeList = ChangeListManager.getInstance(project).getChangeList(selectedChange);
      if (changeList != null) {
        final Collection<Change> changesInList = changeList.getChanges();
        changes = changesInList.toArray(new Change[changesInList.size()]);
        for(int i=0; i<changes.length; i++) {
          if (changes [i] == selectedChange) { 
            index = i;
            break;
          }
        }
      }
    }

    showDiffForChange(changes, index, project);
  }

  public static void showDiffForChange(final Change[] changes, final int index, final Project project) {
    showDiffForChange(changes, index, project, AdditionalToolbarActionsFactory.NONE);
  }

  public interface AdditionalToolbarActionsFactory {
    AdditionalToolbarActionsFactory NONE = new AdditionalToolbarActionsFactory() {
      public List<? extends AnAction> createActions(Change change) {
        return Collections.emptyList();
      }
    };
    List<? extends AnAction> createActions(Change change);
  }

  public static void showDiffForChange(final Change[] changes, final int index, final Project project, @Nullable AdditionalToolbarActionsFactory actionsFactory) {
    final DiffTool tool = DiffManager.getInstance().getDiffTool();

    final SimpleDiffRequest diffReq = createDiffRequest(changes, index, project, actionsFactory);
    if (diffReq != null) {
      tool.show(diffReq);
    }
  }

  private static void showDiffForChange(AnActionEvent e,
                                        final Change[] changes,
                                        final int index,
                                        final Project project,
                                        AdditionalToolbarActionsFactory actionsFactory) {
    DiffViewer diffViewer = (DiffViewer)e.getDataContext().getData(DataConstants.DIFF_VIEWER);
    if (diffViewer != null) {
      final SimpleDiffRequest diffReq = createDiffRequest(changes, index, project, actionsFactory);
      if (diffReq != null) {
        diffViewer.setDiffRequest(diffReq);
      }
    }
  }

  @Nullable
  private static SimpleDiffRequest createDiffRequest(final Change[] changes,
                                                     final int index,
                                                     final Project project,
                                                     final AdditionalToolbarActionsFactory actionsFactory) {
    final Change change = changes[index];

    final ContentRevision bRev = change.getBeforeRevision();
    final ContentRevision aRev = change.getAfterRevision();

    if ((bRev != null && (bRev.getFile().getFileType().isBinary() || bRev.getFile().isDirectory())) ||
        (aRev != null && (aRev.getFile().getFileType().isBinary() || aRev.getFile().isDirectory()))) {
      if (bRev != null && bRev.getFile().getFileType() == StdFileTypes.UNKNOWN) {
        if (!checkAssociate(project, bRev.getFile())) return null;
      }
      else if (aRev != null && aRev.getFile().getFileType() == StdFileTypes.UNKNOWN) {
        if (!checkAssociate(project, aRev.getFile())) return null;
      }
      else {
        return null;
      }
    }

    String title = bRev != null ? bRev.getFile().getPath() : aRev != null ? aRev.getFile().getPath() : "Unknown diff";
    final SimpleDiffRequest diffReq = new SimpleDiffRequest(project, title);

    if (changes.length > 1) {
      diffReq.setToolbarAddons(new DiffRequest.ToolbarAddons() {
        public void customize(DiffToolbar toolbar) {
          toolbar.addSeparator();
          toolbar.addAction(new ShowPrevChangeAction(changes, index, project, actionsFactory));
          toolbar.addAction(new ShowNextChangeAction(changes, index, project, actionsFactory));
          if (actionsFactory != null) {
            toolbar.addSeparator();
            for (AnAction action : actionsFactory.createActions(change)) {
              toolbar.addAction(action);
            }
          }
        }
      });
    }

    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        diffReq.setContents(createContent(project, bRev), createContent(project, aRev));
      }
    }, "Getting revisions content", false, project);

    diffReq.setContentTitles("Base version", "Your version");
    return diffReq;
  }

  private static boolean checkAssociate(final Project project, final FilePath file) {
    int rc = Messages.showDialog(project,
                                 VcsBundle.message("diff.unknown.file.type.prompt", file.getName()),
                                 VcsBundle.message("diff.unknown.file.type.title"),
                                 new String[] {
                                   VcsBundle.message("diff.unknown.file.type.associate"),
                                   CommonBundle.getCancelButtonText()
                                 }, 0, Messages.getQuestionIcon());
    if (rc == 0) {
      FileType fileType = FileTypeChooser.associateFileType(file.getName());
      return fileType != null && !fileType.isBinary();
    }
    return false;
  }

  private static class ShowNextChangeAction extends AnAction {
    private Change[] myChanges;
    private int myIndex;
    private Project myProject;
    private AdditionalToolbarActionsFactory myAdditionalActions;

    public ShowNextChangeAction(final Change[] changes, final int index, final Project project, final AdditionalToolbarActionsFactory actionsFactory) {
      super(VcsBundle.message("action.name.compare.next.file"), "", IconLoader.findIcon("/actions/nextfile.png"));
      myAdditionalActions = actionsFactory;
      myChanges = changes;
      myIndex = index;
      myProject = project;
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myIndex < myChanges.length - 1);
    }

    public void actionPerformed(AnActionEvent e) {
      showDiffForChange(e, myChanges, myIndex + 1, myProject, myAdditionalActions);
    }
  }

  private static class ShowPrevChangeAction extends AnAction {
    private Change[] myChanges;
    private int myIndex;
    private Project myProject;
    private AdditionalToolbarActionsFactory myAdditionalActions;

    public ShowPrevChangeAction(final Change[] changes, final int index, final Project project, final AdditionalToolbarActionsFactory actionsFactory) {
      super(VcsBundle.message("action.name.compare.prev.file"), "", IconLoader.findIcon("/actions/prevfile.png"));
      myAdditionalActions = actionsFactory;
      myChanges = changes;
      myIndex = index;
      myProject = project;
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myIndex > 0);
    }

    public void actionPerformed(AnActionEvent e) {
      showDiffForChange(e, myChanges, myIndex - 1, myProject, myAdditionalActions);
    }
  }

  private static DiffContent createContent(Project project, ContentRevision revision) {
    if (revision == null) return new SimpleContent("");
    if (revision instanceof CurrentContentRevision) {
      final CurrentContentRevision current = (CurrentContentRevision)revision;
      final VirtualFile vFile = current.getVirtualFile();
      return vFile != null ? new FileContent(project, vFile) : new SimpleContent("");
    }

    final String revisionContent = revision.getContent();
    SimpleContent content = revisionContent == null
                            ? new SimpleContent("")
                            : new SimpleContent(revisionContent, revision.getFile().getFileType());
    content.setReadOnly(true);
    return content;
  }
}
