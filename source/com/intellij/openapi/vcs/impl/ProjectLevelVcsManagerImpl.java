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

import com.intellij.ProjectTopics;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vcs.checkin.CodeAnalysisBeforeCheckinHandler;
import com.intellij.openapi.vcs.checkin.StandardBeforeCheckinHandler;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.update.ActionInfo;
import com.intellij.openapi.vcs.update.UpdateInfoTree;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.ContentsUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Icons;
import com.intellij.util.Processor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.EditorAdapter;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;

public class ProjectLevelVcsManagerImpl extends ProjectLevelVcsManagerEx implements ProjectComponent, JDOMExternalizable {
  private List<AbstractVcs> myVcss = new ArrayList<AbstractVcs>();
  private AbstractVcs[] myCachedVCSs = null;
  private final Project myProject;
  private final MessageBus myMessageBus;

  private boolean myIsDisposed = false;

  private ContentManager myContentManager;
  private EditorAdapter myEditorAdapter;

  @NonNls private static final String OPTIONS_SETTING = "OptionsSetting";
  @NonNls private static final String CONFIRMATIONS_SETTING = "ConfirmationsSetting";
  @NonNls private static final String VALUE_ATTTIBUTE = "value";
  @NonNls private static final String ID_ATTRIBUTE = "id";

  @NonNls private static final String ELEMENT_MAPPING = "mapping";
  @NonNls private static final String ATTRIBUTE_DIRECTORY = "directory";
  @NonNls private static final String ATTRIBUTE_VCS = "vcs";
  @NonNls private static final String ATTRIBUTE_DEFAULT_PROJECT = "defaultProject";

  private final List<CheckinHandlerFactory> myRegisteredBeforeCheckinHandlers = new ArrayList<CheckinHandlerFactory>();
  private boolean myHaveEmptyContentRevisions = true;
  private EventDispatcher<VcsListener> myEventDispatcher = EventDispatcher.create(VcsListener.class);
  private List<VcsDirectoryMapping> myDirectoryMappings = new ArrayList<VcsDirectoryMapping>();
  private Set<AbstractVcs> myActiveVcss = new HashSet<AbstractVcs>();
  private boolean myMappingsLoaded = false;
  private boolean myHaveLegacyVcsConfiguration = false;
  private boolean myCheckinHandlerFactoriesLoaded = false;

  private volatile int myBackgroundOperationCounter = 0;

  public ProjectLevelVcsManagerImpl(Project project, final MessageBus bus) {
    this(project, new AbstractVcs[0], bus);
  }

  public ProjectLevelVcsManagerImpl(Project project, AbstractVcs[] vcses, MessageBus bus) {
    myProject = project;
    myMessageBus = bus;
    myVcss = new ArrayList<AbstractVcs>(Arrays.asList(vcses));
    doSetDirectoryMapping("", "");
  }

  private final Map<String, VcsShowOptionsSettingImpl> myOptions = new LinkedHashMap<String, VcsShowOptionsSettingImpl>();
  private final Map<String, VcsShowConfirmationOptionImpl> myConfirmations = new LinkedHashMap<String, VcsShowConfirmationOptionImpl>();

  public void initComponent() {
    createSettingFor(VcsConfiguration.StandardOption.ADD);
    createSettingFor(VcsConfiguration.StandardOption.REMOVE);
    createSettingFor(VcsConfiguration.StandardOption.CHECKOUT);
    createSettingFor(VcsConfiguration.StandardOption.UPDATE);
    createSettingFor(VcsConfiguration.StandardOption.STATUS);
    createSettingFor(VcsConfiguration.StandardOption.EDIT);

    myConfirmations.put(VcsConfiguration.StandardConfirmation.ADD.getId(), new VcsShowConfirmationOptionImpl(
      VcsConfiguration.StandardConfirmation.ADD.getId(),
      VcsBundle.message("label.text.when.files.created.with.idea", ApplicationNamesInfo.getInstance().getProductName()),
      VcsBundle.message("radio.after.creation.do.not.add"), VcsBundle.message("radio.after.creation.show.options"),
      VcsBundle.message("radio.after.creation.add.silently")));

    myConfirmations.put(VcsConfiguration.StandardConfirmation.REMOVE.getId(), new VcsShowConfirmationOptionImpl(
      VcsConfiguration.StandardConfirmation.REMOVE.getId(),
      VcsBundle.message("label.text.when.files.are.deleted.with.idea", ApplicationNamesInfo.getInstance().getProductName()),
      VcsBundle.message("radio.after.deletion.do.not.remove"), VcsBundle.message("radio.after.deletion.show.options"),
      VcsBundle.message("radio.after.deletion.remove.silently")));

    restoreReadConfirm(VcsConfiguration.StandardConfirmation.ADD);
    restoreReadConfirm(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  private void restoreReadConfirm(final VcsConfiguration.StandardConfirmation confirm) {
    if (myReadValue.containsKey(confirm.getId())) {
      getConfirmation(confirm).setValue(myReadValue.get(confirm.getId()));
    }
  }

  private void createSettingFor(final VcsConfiguration.StandardOption option) {
    if (!myOptions.containsKey(option.getId())) {
      myOptions.put(option.getId(), new VcsShowOptionsSettingImpl(option));
    }
  }

  public void registerVcs(AbstractVcs vcs) {
    try {
      vcs.loadSettings();
      vcs.start();
    }
    catch (VcsException e) {
      LOG.debug(e);
    }
    if (!myVcss.contains(vcs)) {
      myVcss.add(vcs);
    }
    vcs.getProvidedStatuses();
    myCachedVCSs = null;
  }

  @Nullable
  public AbstractVcs findVcsByName(String name) {
    if (name == null) return null;

    for (AbstractVcs vcs : myVcss) {
      if (vcs.getName().equals(name)) {
        return vcs;
      }
    }
    final VcsEP[] vcsEPs = Extensions.getExtensions(VcsEP.EP_NAME, myProject);
    for(VcsEP ep: vcsEPs) {
      if (ep.name.equals(name)) {
        AbstractVcs vcs = ep.getVcs(myProject);
        if (!myVcss.contains(vcs)) {
          registerVcs(vcs);
        }
        return vcs;
      }
    }

    return null;
  }

  public AbstractVcs[] getAllVcss() {
    final VcsEP[] vcsEPs = Extensions.getExtensions(VcsEP.EP_NAME, myProject);
    for(VcsEP ep: vcsEPs) {
      AbstractVcs vcs = ep.getVcs(myProject);
      if (!myVcss.contains(vcs)) {
        registerVcs(vcs);
      }
    }
    if (myCachedVCSs == null) {
      Collections.sort(myVcss, new Comparator<AbstractVcs>() {
        public int compare(final AbstractVcs o1, final AbstractVcs o2) {
          return o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());
        }
      });
      myCachedVCSs = myVcss.toArray(new AbstractVcs[myVcss.size()]);
    }
    return myCachedVCSs;
  }


  public void disposeComponent() {
  }

  public void projectOpened() {
    myContentManager = PeerFactory.getInstance().getContentFactory().createContentManager(true, myProject);

    registerCheckinHandlerFactory(new CheckinHandlerFactory() {
      public
      @NotNull
      CheckinHandler createHandler(final CheckinProjectPanel panel) {
        return new StandardBeforeCheckinHandler(myProject);
      }
    });
    registerCheckinHandlerFactory(new CheckinHandlerFactory() {
      public
      @NotNull
      CheckinHandler createHandler(final CheckinProjectPanel panel) {
        return new CodeAnalysisBeforeCheckinHandler(myProject, panel);
      }
    });

    initialize();

    final StartupManager manager = StartupManager.getInstance(myProject);
    manager.registerStartupActivity(new Runnable() {
      public void run() {
        if (!myHaveLegacyVcsConfiguration && !myMappingsLoaded) {
          autoDetectVcsMappings();
        }
        updateActiveVcss();
      }
    });
    manager.registerPostStartupActivity(new Runnable() {
      public void run() {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
        if (toolWindowManager != null) { // Can be null in tests
          ToolWindow toolWindow =
            toolWindowManager.registerToolWindow(ToolWindowId.VCS, myContentManager.getComponent(), ToolWindowAnchor.BOTTOM);
          toolWindow.setIcon(Icons.VCS_SMALL_TAB);
          toolWindow.installWatcher(myContentManager);
        }
        if (myMessageBus != null) {
          myMessageBus.connect().subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
            public void moduleAdded(final Project project, final Module module) {
              autoDetectModuleVcsMapping(module);
            }
          });
        }
      }
    });
  }

  public void initialize() {
    final AbstractVcs[] abstractVcses = myVcss.toArray(new AbstractVcs[myVcss.size()]);
    for (AbstractVcs abstractVcs : abstractVcses) {
      registerVcs(abstractVcs);
    }
  }

  public void projectClosed() {
    dispose();
  }

  @NotNull
  public String getComponentName() {
    return "ProjectLevelVcsManager";
  }

  public boolean checkAllFilesAreUnder(AbstractVcs abstractVcs, VirtualFile[] files) {
    if (files == null) return false;
    for (VirtualFile file : files) {
      if (getVcsFor(file) != abstractVcs) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public AbstractVcs getVcsFor(VirtualFile file) {
    VcsDirectoryMapping mapping = getMappingFor(file);
    if (mapping == null) {
      return null;
    }
    final String vcs = mapping.getVcs();
    if (vcs.length() == 0) {
      return null;
    }
    return findVcsByName(vcs);
  }

  @Nullable
  private VcsDirectoryMapping getMappingFor(VirtualFile file) {
    if (file == null) return null;
    if (myProject.isDisposed()) return null;

    if (!(file.getFileSystem() instanceof LocalFileSystem)) {
      return null;
    }

    // performance: calculate file path just once, rather than once per mapping
    String path = file.getPath();

    // scan from bottom-most mapping to topmost
    for(int i = myDirectoryMappings.size()-1; i >= 0; i--) {
      final VcsDirectoryMapping mapping = myDirectoryMappings.get(i);
      if (fileMatchesMapping(file, path, mapping)) {
        return mapping;
      }
    }
    return null;
  }

  @Nullable
  public AbstractVcs getVcsFor(final FilePath file) {
    VirtualFile vFile = findValidParent(file);
    if (vFile != null) {
      return getVcsFor(vFile);
    }
    return null;
  }

  @Nullable
  public VirtualFile getVcsRootFor(final VirtualFile file) {
    VcsDirectoryMapping mapping = getMappingFor(file);
    if (mapping == null) {
      return null;
    }
    final String directory = mapping.getDirectory();
    if (directory.length() == 0) {
      final VirtualFile contentRoot = ProjectRootManager.getInstance(myProject).getFileIndex().getContentRootForFile(file);
      if (contentRoot != null) {
        return contentRoot;
      }
      return myProject.getBaseDir();
    }
    return LocalFileSystem.getInstance().findFileByPath(directory);
  }

  @Nullable
  public VirtualFile getVcsRootFor(final FilePath file) {
    VirtualFile vFile = findValidParent(file);
    if (vFile != null) {
      return getVcsRootFor(vFile);
    }
    return null;
  }

  @Nullable
  private static VirtualFile findValidParent(FilePath file) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    VirtualFile parent = file.getVirtualFile();
    if (parent == null) {
      parent = file.getVirtualFileParent();
    }
    if (parent == null) {
      File ioFile = file.getIOFile();
      do {
        parent = LocalFileSystem.getInstance().findFileByIoFile(ioFile);
        if (parent != null) break;
        ioFile = ioFile.getParentFile();
        if (ioFile == null) return null;
      }
      while (true);
    }
    return parent;
  }

  private boolean fileMatchesMapping(final VirtualFile file, final String path, final VcsDirectoryMapping mapping) {
    if (mapping.getDirectory().length() == 0) {
      return VfsUtil.getModuleForFile(myProject, file) != null;
    }
    return FileUtil.startsWith(path, mapping.getDirectory());
  }

  private void dispose() {
    if (myIsDisposed) return;
    for(AbstractVcs vcs: myActiveVcss) {
      vcs.deactivate();
    }
    myActiveVcss.clear();
    AbstractVcs[] allVcss = myVcss.toArray(new AbstractVcs[myVcss.size()]);
    for (AbstractVcs allVcs : allVcss) {
      unregisterVcs(allVcs);
    }
    try {
      myContentManager = null;

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
    if (myActiveVcss.contains(vcs)) {
      vcs.deactivate();
      myActiveVcss.remove(vcs);
    }
    try {
      vcs.shutdown();
    }
    catch (VcsException e) {
      LOG.info(e);
    }
    myVcss.remove(vcs);
    myCachedVCSs = null;
  }

  public ContentManager getContentManager() {
    return myContentManager;
  }

  @Nullable
  String getBaseVersionContent(final VirtualFile file) {
    final Change change = ChangeListManager.getInstance(myProject).getChange(file);
    if (change != null) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      if (beforeRevision instanceof BinaryContentRevision) {
        return null;
      }
      if (beforeRevision != null) {
        String content;
        try {
          content = beforeRevision.getContent();
        }
        catch(VcsException ex) {
          content = null;
        }
        if (content == null) myHaveEmptyContentRevisions = true;
        return content;
      }
      return null;
    }

    final Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    if (document != null && document.getModificationStamp() != file.getModificationStamp()) {
      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        public String compute() {
          return LoadTextUtil.loadText(file).toString();
        }
      });
    }

    return null;
  }

  public boolean checkVcsIsActive(AbstractVcs vcs) {
    return checkVcsIsActive(vcs.getName());
  }

  public boolean checkVcsIsActive(final String vcsName) {
    for(VcsDirectoryMapping mapping: myDirectoryMappings) {
      if (mapping.getVcs().equals(vcsName)) {
        return true;
      }
    }
    return false;
  }

  public String getPresentableRelativePathFor(final VirtualFile file) {
    if (file == null) return "";
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject)
          .getFileIndex();
        Module module = fileIndex.getModuleForFile(file);
        VirtualFile contentRoot = fileIndex.getContentRootForFile(file);
        if (module == null) return file.getPresentableUrl();
        StringBuffer result = new StringBuffer();
        result.append("[");
        result.append(module.getName());
        result.append("] ");
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

  public AbstractVcs[] getAllActiveVcss() {
    return myActiveVcss.toArray(new AbstractVcs[myActiveVcss.size()]);

  }

  public void addMessageToConsoleWindow(final String message, final TextAttributes attributes) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        getOrCreateConsoleContent(getContentManager());
        myEditorAdapter.appendString(message, attributes);
      }
    }, ModalityState.defaultModalityState());
  }

  private Content getOrCreateConsoleContent(final ContentManager contentManager) {
    final String displayName = VcsBundle.message("vcs.console.toolwindow.display.name");
    Content content = contentManager.findContent(displayName);
    if (content == null) {
      final EditorFactory editorFactory = EditorFactory.getInstance();
      final Editor editor = editorFactory.createViewer(editorFactory.createDocument(""));
      EditorSettings editorSettings = editor.getSettings();
      editorSettings.setLineMarkerAreaShown(false);
      editorSettings.setLineNumbersShown(false);
      editorSettings.setFoldingOutlineShown(false);

      myEditorAdapter = new EditorAdapter(editor, myProject);
      final JComponent panel = editor.getComponent();
      content = PeerFactory.getInstance().getContentFactory().createContent(panel, displayName, true);
      contentManager.addContent(content);

      contentManager.addContentManagerListener(new ContentManagerAdapter() {
        public void contentRemoved(ContentManagerEvent event) {
          if (event.getContent().getComponent() == panel) {
            editorFactory.releaseEditor(editor);
            contentManager.removeContentManagerListener(this);
          }
        }
      });
    }
    return content;
  }

  @NotNull
  public VcsShowSettingOption getOptions(VcsConfiguration.StandardOption option) {
    return myOptions.get(option.getId());
  }

  public List<VcsShowOptionsSettingImpl> getAllOptions() {
    return new ArrayList<VcsShowOptionsSettingImpl>(myOptions.values());
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl");

  @NotNull
  public VcsShowSettingOption getStandardOption(@NotNull VcsConfiguration.StandardOption option, @NotNull AbstractVcs vcs) {
    final VcsShowOptionsSettingImpl options = myOptions.get(option.getId());
    options.addApplicableVcs(vcs);
    return options;
  }

  @NotNull
  public VcsShowSettingOption getOrCreateCustomOption(@NotNull String vcsActionName, @NotNull AbstractVcs vcs) {
    final VcsShowOptionsSettingImpl option = getOrCreateOption(vcsActionName);
    option.addApplicableVcs(vcs);
    return option;
  }

  public void showProjectOperationInfo(final UpdatedFiles updatedFiles, String displayActionName) {
    showUpdateProjectInfo(updatedFiles, displayActionName, ActionInfo.STATUS);
  }

  public void showUpdateProjectInfo(final UpdatedFiles updatedFiles, final String displayActionName, final ActionInfo actionInfo) {
    ContentManager contentManager = getInstanceEx(myProject).getContentManager();
    final UpdateInfoTree updateInfoTree = new UpdateInfoTree(contentManager, null, myProject, updatedFiles, displayActionName, actionInfo);
    Content content = PeerFactory.getInstance().getContentFactory().createContent(updateInfoTree, VcsBundle.message(
      "toolwindow.title.update.action.info", displayActionName), true);
    ContentsUtil.addOrReplaceContent(contentManager, content, true);
    ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.VCS).activate(null);
    updateInfoTree.expandRootChildren();
  }

  public void addMappingFromModule(final Module module, final String activeVcsName) {
    for(VirtualFile file: ModuleRootManager.getInstance(module).getContentRoots()) {
      setDirectoryMapping(file.getPath(), activeVcsName);
    }
    sortDirectoryMappings();
    removeRedundantMappings();
  }

  private void removeRedundantMappings() {
    int i=1;
    while(i < myDirectoryMappings.size()) {
      if (myDirectoryMappings.get(i).getDirectory().startsWith(myDirectoryMappings.get(i-1).getDirectory()) &&
          myDirectoryMappings.get(i).getVcs().equals(myDirectoryMappings.get(i-1).getVcs())) {
        myDirectoryMappings.remove(i);
      }
      else {
        i++;
      }
    }
  }

  public List<VcsDirectoryMapping> getDirectoryMappings() {
    return Collections.unmodifiableList(myDirectoryMappings);
  }

  public List<VcsDirectoryMapping> getDirectoryMappings(final AbstractVcs vcs) {
    final ArrayList<VcsDirectoryMapping> result = new ArrayList<VcsDirectoryMapping>();
    for(VcsDirectoryMapping mapping: myDirectoryMappings) {
      if (vcs.getName().equals(mapping.getVcs())) {
        result.add(mapping);
      }
    }
    return result;
  }

  public void setDirectoryMapping(final String path, final String activeVcsName) {
    if (myMappingsLoaded) return;            // ignore per-module VCS settings if the mapping table was loaded from .ipr
    myHaveLegacyVcsConfiguration = true;
    doSetDirectoryMapping(path, activeVcsName);
  }

  private void doSetDirectoryMapping(final String path, final String activeVcsName) {
    for(VcsDirectoryMapping mapping: myDirectoryMappings) {
      if (mapping.getDirectory().equals(path)) {
        mapping.setVcs(activeVcsName);
        return;
      }
    }

    VcsDirectoryMapping mapping = new VcsDirectoryMapping(path, activeVcsName);
    myDirectoryMappings.add(mapping);
    sortDirectoryMappings();
  }

  private void sortDirectoryMappings() {
    Collections.sort(myDirectoryMappings, new Comparator<VcsDirectoryMapping>() {
      public int compare(final VcsDirectoryMapping m1, final VcsDirectoryMapping m2) {
        return m1.getDirectory().compareTo(m2.getDirectory());
      }
    });
  }

  public void setDirectoryMappings(final List<VcsDirectoryMapping> items) {
    myDirectoryMappings.clear();
    myDirectoryMappings.addAll(items);
    sortDirectoryMappings();
  }

  public void iterateVcsRoot(final VirtualFile root, final Processor<FilePath> iterator) {
    Set<VirtualFile> filesToExclude = null;
    VcsDirectoryMapping mapping = getMappingFor(root);
    if (mapping != null) {
      for(VcsDirectoryMapping otherMapping: myDirectoryMappings) {
        if (otherMapping != mapping && otherMapping.getDirectory().startsWith(mapping.getDirectory())) {
          if (filesToExclude == null) {
            filesToExclude = new HashSet<VirtualFile>();
          }
          filesToExclude.add(LocalFileSystem.getInstance().findFileByPath(otherMapping.getDirectory()));
        }
      }
    }

    iterateChildren(root, iterator, filesToExclude);
  }

  private boolean iterateChildren(final VirtualFile root, final Processor<FilePath> iterator, final Collection<VirtualFile> filesToExclude) {
    if (!iterator.process(new FilePathImpl(root))) {
      return false;
    }
    if (root.isDirectory()) {
      final VirtualFile[] files = root.getChildren();
      for(VirtualFile child: files) {
        if (child.isDirectory()) {
          if (filesToExclude != null && filesToExclude.contains(child)) {
            continue;
          }
          if (ProjectRootManager.getInstance(myProject).getFileIndex().isIgnored(child)) {
            continue;
          }
        }
        if (!iterateChildren(child, iterator, filesToExclude)) {
          return false;
        }
      }
    }
    return true;
  }

  private VcsShowOptionsSettingImpl getOrCreateOption(String actionName) {
    if (!myOptions.containsKey(actionName)) {
      myOptions.put(actionName, new VcsShowOptionsSettingImpl(actionName));
    }
    return myOptions.get(actionName);
  }

  private final Map<String, VcsShowConfirmationOption.Value> myReadValue =
    new com.intellij.util.containers.HashMap<String, VcsShowConfirmationOption.Value>();

  public void readExternal(Element element) throws InvalidDataException {
    List subElements = element.getChildren(OPTIONS_SETTING);
    for (Object o : subElements) {
      if (o instanceof Element) {
        final Element subElement = ((Element)o);
        final String id = subElement.getAttributeValue(ID_ATTRIBUTE);
        final String value = subElement.getAttributeValue(VALUE_ATTTIBUTE);
        if (id != null && value != null) {
          try {
            final boolean booleanValue = Boolean.valueOf(value).booleanValue();
            getOrCreateOption(id).setValue(booleanValue);
          }
          catch (Exception e) {
            //ignore
          }
        }
      }
    }
    myReadValue.clear();
    subElements = element.getChildren(CONFIRMATIONS_SETTING);
    for (Object o : subElements) {
      if (o instanceof Element) {
        final Element subElement = ((Element)o);
        final String id = subElement.getAttributeValue(ID_ATTRIBUTE);
        final String value = subElement.getAttributeValue(VALUE_ATTTIBUTE);
        if (id != null && value != null) {
          try {
            myReadValue.put(id, VcsShowConfirmationOption.Value.fromString(value));
          }
          catch (Exception e) {
            //ignore
          }
        }
      }
    }

  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (VcsShowOptionsSettingImpl setting : myOptions.values()) {
      final Element settingElement = new Element(OPTIONS_SETTING);
      element.addContent(settingElement);
      settingElement.setAttribute(VALUE_ATTTIBUTE, Boolean.toString(setting.getValue()));
      settingElement.setAttribute(ID_ATTRIBUTE, setting.getDisplayName());
    }

    for (VcsShowConfirmationOptionImpl setting : myConfirmations.values()) {
      final Element settingElement = new Element(CONFIRMATIONS_SETTING);
      element.addContent(settingElement);
      settingElement.setAttribute(VALUE_ATTTIBUTE, setting.getValue().toString());
      settingElement.setAttribute(ID_ATTRIBUTE, setting.getDisplayName());
    }

  }

  @NotNull
  public VcsShowConfirmationOption getStandardConfirmation(@NotNull VcsConfiguration.StandardConfirmation option,
                                                           @NotNull AbstractVcs vcs) {
    final VcsShowConfirmationOptionImpl result = myConfirmations.get(option.getId());
    result.addApplicableVcs(vcs);
    return result;
  }

  public List<VcsShowConfirmationOptionImpl> getAllConfirmations() {
    return new ArrayList<VcsShowConfirmationOptionImpl>(myConfirmations.values());
  }

  @NotNull
  public VcsShowConfirmationOptionImpl getConfirmation(VcsConfiguration.StandardConfirmation option) {
    return myConfirmations.get(option.getId());
  }

  public List<CheckinHandlerFactory> getRegisteredCheckinHandlerFactories() {
    if (!myCheckinHandlerFactoriesLoaded) {
      myCheckinHandlerFactoriesLoaded = true;
      Collections.addAll(myRegisteredBeforeCheckinHandlers, Extensions.getExtensions(CheckinHandlerFactory.EP_NAME, myProject));
    }
    return Collections.unmodifiableList(myRegisteredBeforeCheckinHandlers);
  }

  public void registerCheckinHandlerFactory(CheckinHandlerFactory factory) {
    myRegisteredBeforeCheckinHandlers.add(factory);
  }

  public void unregisterCheckinHandlerFactory(CheckinHandlerFactory handler) {
    myRegisteredBeforeCheckinHandlers.remove(handler);
  }

  public void addVcsListener(VcsListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeVcsListener(VcsListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  public void startBackgroundVcsOperation() {
    myBackgroundOperationCounter++;
  }

  public void stopBackgroundVcsOperation() {
    LOG.assertTrue(myBackgroundOperationCounter > 0, "myBackgroundOperationCounter > 0");
    myBackgroundOperationCounter--;
  }

  public boolean isBackgroundVcsOperationRunning() {
    return myBackgroundOperationCounter > 0;
  }

  public VirtualFile[] getRootsUnderVcs(AbstractVcs vcs) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for(VcsDirectoryMapping mapping: myDirectoryMappings) {
      if (mapping.getVcs().equals(vcs.getName())) {
        if (mapping.getDirectory().length() == 0) {
          addDefaultVcsRoots(vcs, result);
        }
        else {
          VirtualFile file = LocalFileSystem.getInstance().findFileByPath(mapping.getDirectory());
          if (file != null) {
            result.add(file);
          }
        }
      }
    }

    Collections.sort(result, new Comparator<VirtualFile>() {
      public int compare(final VirtualFile o1, final VirtualFile o2) {
        return o1.getPath().compareTo(o2.getPath());
      }
    });
    int i=1;
    while(i < result.size()) {
      if (VfsUtil.isAncestor(result.get(i-1), result.get(i), false)) {
        result.remove(i);
      }
      else {
        i++;
      }
    }
    return result.toArray(new VirtualFile[result.size()]);
  }

  public VirtualFile[] getAllVersionedRoots() {
    List<VirtualFile> vFiles = new ArrayList<VirtualFile>();
    for(AbstractVcs vcs: myActiveVcss) {
      Collections.addAll(vFiles, getRootsUnderVcs(vcs));
    }
    return vFiles.toArray(new VirtualFile[vFiles.size()]);
  }

  private void addDefaultVcsRoots(final AbstractVcs vcs, final List<VirtualFile> result) {
    final VirtualFile baseDir = myProject.getBaseDir();
    if (getVcsFor(baseDir) == vcs) {
      result.add(baseDir);
    }
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for(Module module: modules) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
      for(VirtualFile file: files) {
        if (getVcsFor(file) == vcs) {
          result.add(file);
        }
      }
    }
  }

  public void updateActiveVcss() {
    HashSet<AbstractVcs> oldActiveVcss = new HashSet<AbstractVcs>(myActiveVcss);
    myActiveVcss.clear();
    for(VcsDirectoryMapping mapping: myDirectoryMappings) {
      if (mapping.getVcs().length() > 0) {
        AbstractVcs vcs = findVcsByName(mapping.getVcs());
        if (vcs != null && !myActiveVcss.contains(vcs)) {
          myActiveVcss.add(vcs);
          if (!oldActiveVcss.contains(vcs)) {
            vcs.activate();
          }
        }
      }
    }

    for(AbstractVcs vcs: oldActiveVcss) {
      if (!myActiveVcss.contains(vcs)) {
        vcs.deactivate();
      }
    }

    notifyDirectoryMappingChanged();
  }

  public void notifyDirectoryMappingChanged() {
    myEventDispatcher.getMulticaster().directoryMappingChanged();
  }

  boolean hasEmptyContentRevisions() {
    return myHaveEmptyContentRevisions;
  }

  void resetHaveEmptyContentRevisions() {
    myHaveEmptyContentRevisions = false;
  }

  public void readDirectoryMappings(final Element element) {
    myDirectoryMappings.clear();
    final List list = element.getChildren(ELEMENT_MAPPING);
    boolean haveNonEmptyMappings = false;
    for(Object childObj: list) {
      Element child = (Element) childObj;
      final String vcs = child.getAttributeValue(ATTRIBUTE_VCS);
      if (vcs != null && vcs.length() > 0) {
        haveNonEmptyMappings = true;
      }
      VcsDirectoryMapping mapping = new VcsDirectoryMapping(child.getAttributeValue(ATTRIBUTE_DIRECTORY), vcs);
      myDirectoryMappings.add(mapping);
    }
    boolean defaultProject = Boolean.TRUE.toString().equals(element.getAttributeValue(ATTRIBUTE_DEFAULT_PROJECT));
    // run autodetection if there's no VCS in default project and 
    if (haveNonEmptyMappings || !defaultProject) {
      myMappingsLoaded = true;
    }
  }

  public void writeDirectoryMappings(final Element element) {
    if (myProject.isDefault()) {
      element.setAttribute(ATTRIBUTE_DEFAULT_PROJECT, Boolean.TRUE.toString());
    }
    for(VcsDirectoryMapping mapping: myDirectoryMappings) {
      Element child = new Element(ELEMENT_MAPPING);
      child.setAttribute(ATTRIBUTE_DIRECTORY, mapping.getDirectory());
      child.setAttribute(ATTRIBUTE_VCS, mapping.getVcs());
      element.addContent(child);
    }
  }

  @Nullable
  private AbstractVcs findVersioningVcs(VirtualFile file) {
    for(AbstractVcs vcs: getAllVcss()) {
      if (vcs.isVersionedDirectory(file)) {
        return vcs;
      }
    }
    return null;
  }

  private void autoDetectVcsMappings() {
    Set<AbstractVcs> usedVcses = new HashSet<AbstractVcs>();
    Map<VirtualFile, AbstractVcs> vcsMap = new HashMap<VirtualFile, AbstractVcs>();
    for(Module module: ModuleManager.getInstance(myProject).getModules()) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
      for(VirtualFile file: files) {
        AbstractVcs contentRootVcs = findVersioningVcs(file);
        if (contentRootVcs != null) {
          vcsMap.put(file, contentRootVcs);
        }
        usedVcses.add(contentRootVcs);
      }
    }
    if (usedVcses.size() == 1) {
      final AbstractVcs[] abstractVcses = usedVcses.toArray(new AbstractVcs[1]);
      if (abstractVcses [0] != null) {
        doSetDirectoryMapping("", abstractVcses [0].getName());
      }
    }
    else {
      for(Map.Entry<VirtualFile, AbstractVcs> entry: vcsMap.entrySet()) {
        doSetDirectoryMapping(entry.getKey().getPath(), entry.getValue() == null ? "" : entry.getValue().getName());
      }
      sortDirectoryMappings();
      removeRedundantMappings();
    }
  }

  private void autoDetectModuleVcsMapping(final Module module) {
    final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
    for(VirtualFile file: files) {
      AbstractVcs vcs = findVersioningVcs(file);
      if (vcs != null && vcs != getVcsFor(file)) {
        doSetDirectoryMapping(file.getPath(), vcs.getName());
      }
    }
    sortDirectoryMappings();
    removeRedundantMappings();
    updateActiveVcss();
  }
}
