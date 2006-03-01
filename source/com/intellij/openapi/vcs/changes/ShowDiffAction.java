package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author max
 */
public class ShowDiffAction extends AnAction {
  public ShowDiffAction() {
    super("Show Diff", "Show diff for selected change", IconLoader.getIcon("/actions/diff.png"));
  }

  public void update(AnActionEvent e) {
    Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    e.getPresentation().setEnabled(project != null && changes != null && changes.length == 1);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
    if (project == null || changes == null) return;

    Change change = changes[0];

    showDiffForChange(change, project);
  }

  public static void showDiffForChange(final Change change, final Project project) {
    final DiffTool tool = DiffManager.getInstance().getDiffTool();

    final ContentRevision bRev = change.getBeforeRevision();
    final ContentRevision aRev = change.getAfterRevision();

    if (bRev != null && bRev.getFile().getFileType().isBinary() || aRev != null && aRev.getFile().getFileType().isBinary()) {
      return;
    }

    String title = bRev != null ? bRev.getFile().getPath() : aRev != null ? aRev.getFile().getPath() : "Unknown diff";
    final SimpleDiffRequest diffReq = new SimpleDiffRequest(project, title);

    diffReq.setContents(createContent(project, bRev), createContent(project, aRev));
    diffReq.setContentTitles("Base version", "Your version");
    tool.show(diffReq);
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
