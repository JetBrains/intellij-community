/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LvcsFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.fileView.impl.VirtualAndPsiFileDataProvider;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.ContentManager;

import java.io.File;
import java.util.*;

public class ProjectLevelVcsManagerImpl extends ProjectLevelVcsManagerEx implements ProjectComponent {

  private List<AbstractVcs> myVcss = new ArrayList<AbstractVcs>();
  private final Project myProject;
  private final Map<AbstractVcs, Boolean> myVcsToStatus = new HashMap<AbstractVcs, Boolean>();
  private com.intellij.util.containers.HashMap<Document, LineStatusTracker> myLineStatusTrackers =
    new com.intellij.util.containers.HashMap<Document, LineStatusTracker>();
  private boolean myIsDisposed = false;

  private EditorFactoryListener myEditorFactoryListener = new MyEditorFactoryListener();
  private final MyFileStatusListener myFileStatusListener = new MyFileStatusListener();
  private final MyVirtualFileListener myVirtualFileListener = new MyVirtualFileListener();
  private ContentManager myContentManager;


  public ProjectLevelVcsManagerImpl(Project project) {
    myProject = project;
  }

  public void initComponent() { }

  public void registerVcs(AbstractVcs vcs) {
    try {
      vcs.start();
      myVcsToStatus.put(vcs, Boolean.TRUE);
    }
    catch (VcsException e) {
      myVcsToStatus.put(vcs, Boolean.FALSE);
    }
    myVcss.add(vcs);
  }

  public AbstractVcs findVcsByName(String name) {
    if (name == null) return null;

    for (Iterator<AbstractVcs> iterator = myVcss.iterator(); iterator.hasNext();) {
      AbstractVcs vcs = iterator.next();
      if (vcs.getName().equals(name)) {
        return vcs;
      }
    }

    return null;
  }

  public AbstractVcs[] getAllVcss() {
    return myVcss.toArray(new AbstractVcs[myVcss.size()]);
  }


  public void disposeComponent() {
  }

  public void projectOpened() {
    myIsDisposed = false;
    myContentManager = PeerFactory.getInstance().getContentFactory().createContentManager(true, myProject);
    myLineStatusTrackers = new com.intellij.util.containers.HashMap<Document, LineStatusTracker>();

    Object[] components = myProject.getComponents(AbstractVcs.class);
    for (int i = 0; i < components.length; i++) {
      AbstractVcs vcs = (AbstractVcs)components[i];
      registerVcs(vcs);
    }

    EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener);
    FileStatusManager.getInstance(getProject()).addFileStatusListener(myFileStatusListener);
    VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener);

    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
        if (toolWindowManager != null) { // Can be null in tests
          ToolWindow toolWindow = toolWindowManager.registerToolWindow(ToolWindowId.VCS, myContentManager.getComponent(),
                                                                       ToolWindowAnchor.BOTTOM);
          toolWindow.setIcon(IconLoader.getIcon("/_cvs/cvs.png"));
          toolWindow.installWatcher(myContentManager);
        }
      }
    });
  }

  public void projectClosed() {
    dispose();
  }

  public String getComponentName() {
    return "ProjectLevelVcsManager";
  }

  public boolean checkAllFielsAreUnder(AbstractVcs abstractVcs, VirtualFile[] files) {
    if (files == null) return false;
    for (int i = 0; i < files.length; i++) {
      if (ProjectLevelVcsManager.getInstance(myProject).getVcsFor(files[i]) != abstractVcs) {
        return false;
      }
    }
    return true;
  }

  public AbstractVcs getVcsFor(VirtualFile file) {
    if (file == null) return null;
    if (myProject.isDisposed()) return null;
    Module module = VfsUtil.getModuleForFile(myProject, file);
    if (module == null) return null;
    return ModuleLevelVcsManager.getInstance(module).getActiveVcs();
  }

  private void dispose() {
    if (myIsDisposed) return;
    AbstractVcs[] allVcss = getAllVcss();
    for (int i = 0; i < allVcss.length; i++) {
      unregisterVcs(allVcss[i]);
    }
    try {
      final Collection<LineStatusTracker> trackers = myLineStatusTrackers.values();
      final LineStatusTracker[] lineStatusTrackers = trackers.toArray(new LineStatusTracker[trackers.size()]);
      for (int i = 0; i < lineStatusTrackers.length; i++) {
        LineStatusTracker tracker = lineStatusTrackers[i];
        releaseTracker(tracker.getDocument());
      }

      myLineStatusTrackers = null;
      myContentManager = null;

      EditorFactory.getInstance().removeEditorFactoryListener(myEditorFactoryListener);
      FileStatusManager.getInstance(getProject()).removeFileStatusListener(myFileStatusListener);
      VirtualFileManager.getInstance().removeVirtualFileListener(myVirtualFileListener);

      ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
      if (toolWindowManager != null && toolWindowManager.getToolWindow(ToolWindowId.VCS) != null) {
        toolWindowManager.unregisterToolWindow(ToolWindowId.VCS);
      }
    }
    finally {
      myIsDisposed = true;
    }

  }

  public void unregisterVcs(AbstractVcs vcs) {
    try {
      vcs.shutdown();
    }
    catch (VcsException e) {
      e.printStackTrace();
    }
    myVcss.remove(vcs);
  }

  private Project getProject() {
    return myProject;
  }

  public synchronized void setLineStatusTracker(Document document, LineStatusTracker tracker) {
    myLineStatusTrackers.put(document, tracker);
  }

  public LineStatusTracker getLineStatusTracker(Document document) {
    if (myLineStatusTrackers == null) return null;
    return myLineStatusTrackers.get(document);
  }


  public LineStatusTracker setUpToDateContent(Document document, String lastUpToDateContent) {
    LineStatusTracker result = myLineStatusTrackers.get(document);
    if (result == null) {
      result = LineStatusTracker.createOn(document, lastUpToDateContent, getProject());
      myLineStatusTrackers.put(document, result);
    }
    return result;
  }

  public ContentManager getContentManager() {
    return myContentManager;
  }

  private synchronized void resetTracker(final VirtualFile virtualFile) {
    final LvcsFile lvcsFile = LocalVcs.getInstance(getProject()).findFile(virtualFile.getPath());
    if (lvcsFile == null) return;

    final Document document = FileDocumentManager.getInstance().getCachedDocument(virtualFile);
    if (document == null) return;

    final LineStatusTracker tracker = myLineStatusTrackers.get(document);
    if (tracker != null) {
      resetTracker(tracker);
    }
    else {
      if (Arrays.asList(FileEditorManager.getInstance(getProject()).getOpenFiles()).contains(virtualFile)) {
        installTracker(virtualFile, document);
      }
    }
  }

  private synchronized boolean releaseTracker(Document document) {
    if (myLineStatusTrackers == null) return false;
    if (!myLineStatusTrackers.containsKey(document)) return false;
    LineStatusTracker tracker = myLineStatusTrackers.remove(document);
    tracker.release();
    return true;
  }

  public synchronized void resetTracker(final LineStatusTracker tracker) {
    if (tracker != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myIsDisposed) return;
          if (releaseTracker(tracker.getDocument())) {
            installTracker(tracker.getVirtualFile(), tracker.getDocument());
          }
        }
      });
    }
  }

  private class MyFileStatusListener implements FileStatusListener {
    public void fileStatusesChanged() {
      if (myIsDisposed) return;
      synchronized (this) {
        List<LineStatusTracker> trackers = new ArrayList<LineStatusTracker>(myLineStatusTrackers.values());
        for (Iterator<LineStatusTracker> i = trackers.iterator(); i.hasNext();) {
          LineStatusTracker tracker = i.next();
          resetTracker(tracker);
        }
      }
    }

    public void fileStatusChanged(VirtualFile virtualFile) {
      resetTracker(virtualFile);
    }
  }

  private class MyEditorFactoryListener extends EditorFactoryAdapter {
    public void editorCreated(EditorFactoryEvent event) {
      Editor editor = event.getEditor();
      Document document = editor.getDocument();
      VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
      installTracker(virtualFile, document);
    }

    public void editorReleased(EditorFactoryEvent event) {
      final Editor editor = event.getEditor();
      final Document doc = editor.getDocument();
      final Editor[] editors = event.getFactory().getEditors(doc, getProject());
      if (editors.length == 0) {
        releaseTracker(doc);
      }
    }
  }


  private class MyVirtualFileListener extends VirtualFileAdapter {
    public void beforeContentsChange(VirtualFileEvent event) {
      if (event.getRequestor() == null) {
        resetTracker(event.getFile());
      }
    }
  }

  private synchronized void installTracker(VirtualFile virtualFile, Document document) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    AbstractVcs activeVcs = getVcsFor(virtualFile);

    if (activeVcs == null) return;
    if (virtualFile == null) return;

    if (!(virtualFile.getFileSystem() instanceof LocalFileSystem)) return;

    UpToDateRevisionProvider upToDateRevisionProvider = activeVcs.getUpToDateRevisionProvider();
    if (upToDateRevisionProvider == null) return;

    String lastUpToDateContent = upToDateRevisionProvider.getLastUpToDateContentFor(virtualFile);
    if (lastUpToDateContent == null) return;

    setUpToDateContent(document, lastUpToDateContent);
  }

  public boolean checkVcsIsActive(AbstractVcs vcs) {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (int i = 0; i < modules.length; i++) {
      Module module = modules[i];
      if (ModuleLevelVcsManager.getInstance(module).getActiveVcs() == vcs) return true;
    }
    return false;
  }

  public String getPresentableRelativePathFor(final VirtualFile file) {
    if (file == null) return "";
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
        Module module = fileIndex.getModuleForFile(file);
        VirtualFile contentRoot = fileIndex.getContentRootForFile(file);
        if (module == null) return file.getPresentableUrl();
        StringBuffer result = new StringBuffer();
        result.append("<");
        result.append(module.getName());
        result.append(">");
        result.append(File.separatorChar);
        result.append(contentRoot.getName());
        String relativePath = VfsUtil.getRelativePath(file, contentRoot, File.separatorChar);
        if (relativePath.length() > 0) {
          result.append(File.separatorChar);
          result.append(relativePath);
        }
        return result.toString();
      }
    });
  }

  public DataProvider createVirtualAndPsiFileDataProvider(VirtualFile[] virtualFileArray, VirtualFile selectedFile) {
    return new VirtualAndPsiFileDataProvider(myProject, virtualFileArray, selectedFile);
  }

  public Module[] getAllModulesUnder(AbstractVcs vcs) {
    ArrayList<Module> result = new ArrayList<Module>();
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (int i = 0; i < modules.length; i++) {
      Module module = modules[i];
      if (ModuleLevelVcsManager.getInstance(module).getActiveVcs() == vcs) {
        result.add(module);
      }
    }
    return result.toArray(new Module[result.size()]);
  }

  public AbstractVcs[] getAllActiveVcss() {
    ArrayList<AbstractVcs> result = new ArrayList<AbstractVcs>();
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (int i = 0; i < modules.length; i++) {
      Module module = modules[i];
      AbstractVcs activeVcs = ModuleLevelVcsManager.getInstance(module).getActiveVcs();
      if (activeVcs != null && !result.contains(activeVcs)) {
        result.add(activeVcs);
      }
    }
    return result.toArray(new AbstractVcs[result.size()]);

  }

}
