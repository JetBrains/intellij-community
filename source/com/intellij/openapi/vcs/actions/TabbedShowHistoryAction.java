package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
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

  protected FilePath[] getSelectedFiles(VcsContext context) {
    ArrayList<FilePath> result = new ArrayList<FilePath>();
    VirtualFile[] virtualFileArray = context.getSelectedFiles();
    if (virtualFileArray != null) {
      for (int i = 0; i < virtualFileArray.length; i++) {
        VirtualFile virtualFile = virtualFileArray[i];
        result.add(new FilePathImpl(virtualFile));
      }
    }

    File[] fileArray = context.getSelectedIOFiles();
    if (fileArray != null) {
      for (int i = 0; i < fileArray.length; i++) {
        File file = fileArray[i];
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

      String actionName = "File " + path.getName() + " History";

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
    Messages.showMessageDialog(exception.getLocalizedMessage(), "Could Not Load File History", Messages.getErrorIcon());
  }
}
