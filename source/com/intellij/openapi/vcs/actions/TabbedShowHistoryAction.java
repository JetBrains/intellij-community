package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ContentsUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class TabbedShowHistoryAction extends AbstractVcsAction {
  protected void update(VcsContext context, Presentation presentation) {
    presentation.setVisible(isVisible(context));
    presentation.setEnabled(isEnabled(context));
  }

  protected boolean isVisible(VcsContext context) {
    return true;
  }

  protected VcsHistoryProvider getProvider(AbstractVcs activeVcs) {
    return activeVcs.getVcsHistoryProvider();
  }

  protected boolean isEnabled(VcsContext context) {
    FilePath[] selectedFiles = getSelectedFiles(context);
    if (selectedFiles == null) return false;
    if (selectedFiles.length != 1) return false;
    if (selectedFiles[0].isDirectory()) return false;
    FilePath path = selectedFiles[0];
    Project project = context.getProject();
    if (project == null) return false;
    VirtualFile someVFile = path.getVirtualFile() != null ? path.getVirtualFile() : path.getVirtualFileParent();
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(someVFile);
    if (vcs == null) return false;
    VcsHistoryProvider vcsHistoryProvider = getProvider(vcs);
    if (vcsHistoryProvider == null) return false;
    return vcs.fileExistsInVcs(path) && isVisible(context);
  }

  protected static FilePath[] getSelectedFiles(VcsContext context) {
    ArrayList<FilePath> result = new ArrayList<FilePath>();
    VirtualFile[] virtualFileArray = context.getSelectedFiles();
    if (virtualFileArray != null) {
      for (VirtualFile virtualFile : virtualFileArray) {
        result.add(new FilePathImpl(virtualFile));
      }
    }

    File[] fileArray = context.getSelectedIOFiles();
    if (fileArray != null) {
      for (File file : fileArray) {
        VirtualFile parent = LocalFileSystem.getInstance().findFileByIoFile(file.getParentFile());
        if (parent != null) {
          result.add(new FilePathImpl(parent, file.getName()));
        }
      }
    }
    return result.toArray(new FilePath[result.size()]);
  }

  protected void actionPerformed(VcsContext context) {
    FilePath path = getSelectedFiles(context)[0];
    Project project = context.getProject();
    VirtualFile someVFile = path.getVirtualFile() != null ? path.getVirtualFile() : path.getVirtualFileParent();
    AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(someVFile);
    VcsHistoryProvider vcsHistoryProvider = getProvider(activeVcs);
    try {
      VcsHistorySession session = vcsHistoryProvider.createSessionFor(path);
      List<VcsFileRevision> revisionsList = session.getRevisionList();
      if (revisionsList.isEmpty()) return;

      String actionName = VcsBundle.message("action.name.file.history", path.getName());

      ContentManager contentManager = ProjectLevelVcsManagerEx.getInstanceEx(project).getContentManager();

      FileHistoryPanelImpl fileHistoryPanel = new FileHistoryPanelImpl(project,
                                                                       path, session, activeVcs, contentManager);
      Content content = PeerFactory.getInstance().getContentFactory().createContent(fileHistoryPanel, actionName, true);
      ContentsUtil.addOrReplaceContent(contentManager, content, true);

      ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);
      toolWindow.activate(null);
    }
    catch (Exception exception) {
      reportError(exception);
    }
  }

  protected void reportError(Exception exception) {
    exception.printStackTrace();
    Messages.showMessageDialog(exception.getLocalizedMessage(), VcsBundle.message("message.title.could.not.load.file.history"), Messages.getErrorIcon());
  }

  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }
}
