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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.DisposableEditorPanel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vcs.checkout.CompositeCheckoutListener;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.impl.projectlevelman.*;
import com.intellij.openapi.vcs.update.ActionInfo;
import com.intellij.openapi.vcs.update.UpdateInfoTree;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ContentsUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Icons;
import com.intellij.util.Processor;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.EditorAdapter;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ProjectLevelVcsManagerImpl extends ProjectLevelVcsManagerEx implements ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl");
  
  private final ProjectLevelVcsManagerSerialization mySerialization;
  private final OptionsAndConfirmations myOptionsAndConfirmations;

  private AllVcsesI myAllVcsesI;
  private NewMappings myMappings;
  private final Project myProject;
  private MappingsToRoots myMappingsToRoots;

  private volatile boolean myIsDisposed = false;
  private final Object myDisposeLock = new Object();

  private ContentManager myContentManager;
  private EditorAdapter myEditorAdapter;

  @NonNls private static final String ELEMENT_MAPPING = "mapping";
  @NonNls private static final String ATTRIBUTE_DIRECTORY = "directory";
  @NonNls private static final String ATTRIBUTE_VCS = "vcs";
  @NonNls private static final String ATTRIBUTE_DEFAULT_PROJECT = "defaultProject";
  @NonNls private static final String ELEMENT_ROOT_SETTINGS = "rootSettings";
  @NonNls private static final String ATTRIBUTE_CLASS = "class";

  private final List<CheckinHandlerFactory> myRegisteredBeforeCheckinHandlers = new ArrayList<CheckinHandlerFactory>();
  private final EventDispatcher<VcsListener> myEventDispatcher = EventDispatcher.create(VcsListener.class);
  private boolean myMappingsLoaded = false;
  private boolean myHaveLegacyVcsConfiguration = false;
  private boolean myCheckinHandlerFactoriesLoaded = false;
  private DefaultVcsRootPolicy myDefaultVcsRootPolicy;

  private volatile int myBackgroundOperationCounter = 0;

  private final Map<MyBackgroundableActions, BackgroundableActionEnabledHandler> myBackgroundableActionHandlerMap;

  public ProjectLevelVcsManagerImpl(Project project) {
    myProject = project;
    mySerialization = new ProjectLevelVcsManagerSerialization();
    myOptionsAndConfirmations = new OptionsAndConfirmations();

    myDefaultVcsRootPolicy = DefaultVcsRootPolicy.getInstance(project);

    myAllVcsesI = new AllVcsesCorrectlyInitProxy(myProject);
    myMappings = new NewMappings(myProject, myAllVcsesI, myEventDispatcher);
    myMappingsToRoots = new MappingsToRoots(myMappings, myProject);
    myBackgroundableActionHandlerMap = new HashMap<MyBackgroundableActions, BackgroundableActionEnabledHandler>();
  }

  public void initComponent() {
    myOptionsAndConfirmations.init(new Convertor<String, VcsShowConfirmationOption.Value>() {
      public VcsShowConfirmationOption.Value convert(String o) {
        return mySerialization.getInitOptionValue(o);
      }
    });
  }

  public void registerVcs(AbstractVcs vcs) {
    myAllVcsesI.registerManually(vcs);
  }

  @Nullable
  public AbstractVcs findVcsByName(String name) {
    if (name == null) return null;
    if (myProject.isDisposed()) return null;
    return myAllVcsesI.getByName(name);
  }

  public AbstractVcs[] getAllVcss() {
    return myAllVcsesI.getAll();
  }

  public boolean haveVcses() {
    return ! myAllVcsesI.isEmpty();
  }

  public void disposeComponent() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      dispose();
    }
  }

  public void projectOpened() {
    final StartupManager manager = StartupManager.getInstance(myProject);
    manager.registerPostStartupActivity(new DumbAwareRunnable() {
      public void run() {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
        if (toolWindowManager != null) { // Can be null in tests
          ToolWindow toolWindow =
            toolWindowManager.registerToolWindow(ToolWindowId.VCS, true, ToolWindowAnchor.BOTTOM, myProject, true);
          myContentManager = toolWindow.getContentManager();
          toolWindow.setIcon(Icons.VCS_SMALL_TAB);
          toolWindow.installWatcher(myContentManager);
        } else {
          myContentManager = ContentFactory.SERVICE.getInstance().createContentManager(true, myProject);
        }
      }
    });
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
  public AbstractVcs getVcsFor(@NotNull VirtualFile file) {
    final String vcsName = myMappings.getVcsFor(file);
    if (vcsName == null || vcsName.length() == 0) {
      return null;
    }
    return myAllVcsesI.getByName(vcsName);
  }

  @Nullable
  public AbstractVcs getVcsFor(final FilePath file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<AbstractVcs>() {
      @Nullable
      public AbstractVcs compute() {
        if (! myProject.isInitialized()) return null;
        if (myProject.isDisposed()) throw new ProcessCanceledException();
        VirtualFile vFile = ChangesUtil.findValidParent(file);
        if (vFile != null) {
          return getVcsFor(vFile);
        }
        return null;
      }
    });
  }

  @Nullable
  public VirtualFile getVcsRootFor(final VirtualFile file) {
    final VcsDirectoryMapping mapping = myMappings.getMappingFor(file);
    if (mapping == null) {
      return null;
    }
    final String directory = mapping.getDirectory();
    if (directory.length() == 0) {
      return myDefaultVcsRootPolicy.getVcsRootFor(file);
    }
    return LocalFileSystem.getInstance().findFileByPath(directory);
  }

  @Nullable
  public VirtualFile getVcsRootFor(final FilePath file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
      @Nullable
      public VirtualFile compute() {
        VirtualFile vFile = ChangesUtil.findValidParent(file);
        if (vFile != null) {
          return getVcsRootFor(vFile);
        }
        return null;
      }
    });
  }

  private void dispose() {
    // todo dispose lock is bad here..
    synchronized (myDisposeLock) {
      if (myIsDisposed) return;

      myMappings.disposeMe();
      myAllVcsesI.disposeMe();

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
  }

  public void unregisterVcs(AbstractVcs vcs) {
    if ((! ApplicationManager.getApplication().isUnitTestMode()) && (myMappings.haveActiveVcs(vcs.getName()))) {
      // unlikely
      LOG.warn("Active vcs '" + vcs.getName() + "' is being unregistered. Remove from mappings first.");
    }
    myMappings.beingUnregistered(vcs.getName());
    myAllVcsesI.unregisterManually(vcs);
  }

  public ContentManager getContentManager() {
    return myContentManager;
  }

  public boolean checkVcsIsActive(AbstractVcs vcs) {
    return checkVcsIsActive(vcs.getName());
  }

  public boolean checkVcsIsActive(final String vcsName) {
    return myMappings.haveActiveVcs(vcsName);
  }

  public AbstractVcs[] getAllActiveVcss() {
    return myMappings.getActiveVcses();
  }

  public void addMessageToConsoleWindow(final String message, final TextAttributes attributes) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        // for default and disposed projects the ContentManager is not available.
        if (myProject.isDisposed() || myProject.isDefault()) return;
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
      content = ContentFactory.SERVICE.getInstance().createContent(new DisposableEditorPanel(editor), displayName, true);
      contentManager.addContent(content);
    }
    return content;
  }

  @NotNull
  public VcsShowSettingOption getOptions(VcsConfiguration.StandardOption option) {
    return myOptionsAndConfirmations.getOptions(option);
  }

  public List<VcsShowOptionsSettingImpl> getAllOptions() {
    return myOptionsAndConfirmations.getAllOptions();
  }

  @NotNull
  public VcsShowSettingOption getStandardOption(@NotNull VcsConfiguration.StandardOption option, @NotNull AbstractVcs vcs) {
    final VcsShowOptionsSettingImpl options = (VcsShowOptionsSettingImpl) getOptions(option);
    options.addApplicableVcs(vcs);
    return options;
  }

  @NotNull
  public VcsShowSettingOption getOrCreateCustomOption(@NotNull String vcsActionName, @NotNull AbstractVcs vcs) {
    return myOptionsAndConfirmations.getOrCreateCustomOption(vcsActionName, vcs);
  }

  public void showProjectOperationInfo(final UpdatedFiles updatedFiles, String displayActionName) {
    showUpdateProjectInfo(updatedFiles, displayActionName, ActionInfo.STATUS);
  }

  public UpdateInfoTree showUpdateProjectInfo(UpdatedFiles updatedFiles, String displayActionName, ActionInfo actionInfo) {
    synchronized (myDisposeLock) {
      if (myIsDisposed) return null;
      ContentManager contentManager = getContentManager();
      if (contentManager == null) {
        return null;  // content manager is made null during dispose; flag is set later
      }
      final UpdateInfoTree updateInfoTree = new UpdateInfoTree(contentManager, myProject, updatedFiles, displayActionName, actionInfo);
      Content content = ContentFactory.SERVICE.getInstance().createContent(updateInfoTree, VcsBundle.message(
        "toolwindow.title.update.action.info", displayActionName), true);
      Disposer.register(content, updateInfoTree);
      ContentsUtil.addOrReplaceContent(contentManager, content, true);
      ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.VCS).activate(null);
      updateInfoTree.expandRootChildren();
      return updateInfoTree;
    }
  }

  public void cleanupMappings() {
    myMappings.cleanupMappings();
  }

  public List<VcsDirectoryMapping> getDirectoryMappings() {
    return myMappings.getDirectoryMappings();
  }

  public List<VcsDirectoryMapping> getDirectoryMappings(final AbstractVcs vcs) {
    return myMappings.getDirectoryMappings(vcs.getName());
  }

  @Nullable
  public VcsDirectoryMapping getDirectoryMappingFor(final FilePath path) {
    return ApplicationManager.getApplication().runReadAction(new Computable<VcsDirectoryMapping>() {
      @Nullable
      public VcsDirectoryMapping compute() {
        VirtualFile vFile = ChangesUtil.findValidParent(path);
        if (vFile != null) {
          return myMappings.getMappingFor(vFile);
        }
        return null;
      }
    });
  }

  public boolean hasExplicitMapping(final FilePath f) {
    VirtualFile vFile = ChangesUtil.findValidParent(f);
    if (vFile == null) return false;
    return hasExplicitMapping(vFile);
  }

  public boolean hasExplicitMapping(final VirtualFile vFile) {
    final VcsDirectoryMapping mapping = myMappings.getMappingFor(vFile);
    return mapping != null && (! mapping.isDefaultMapping());
  }

  public void setDirectoryMapping(final String path, final String activeVcsName) {
    if (myMappingsLoaded) return;            // ignore per-module VCS settings if the mapping table was loaded from .ipr
    myHaveLegacyVcsConfiguration = true;
    myMappings.setMapping(FileUtil.toSystemIndependentName(path), activeVcsName);
  }

  public void setAutoDirectoryMapping(String path, String activeVcsName) {
    myMappings.setMapping(path, activeVcsName);
  }

  public void removeDirectoryMapping(VcsDirectoryMapping mapping) {
    myMappings.removeDirectoryMapping(mapping);
  }

  public void setDirectoryMappings(final List<VcsDirectoryMapping> items) {
    myMappings.setDirectoryMappings(items);
  }

  public void iterateVcsRoot(final VirtualFile root, final Processor<FilePath> iterator) {
    VcsRootIterator.iterateVcsRoot(myProject, root, iterator);
  }

  public void readExternal(Element element) throws InvalidDataException {
    mySerialization.readExternalUtil(element, myOptionsAndConfirmations);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    mySerialization.writeExternalUtil(element, myOptionsAndConfirmations);
  }

  @NotNull
  public VcsShowConfirmationOption getStandardConfirmation(@NotNull VcsConfiguration.StandardConfirmation option,
                                                           @NotNull AbstractVcs vcs) {
    final VcsShowConfirmationOptionImpl result = getConfirmation(option);
    result.addApplicableVcs(vcs);
    return result;
  }

  public List<VcsShowConfirmationOptionImpl> getAllConfirmations() {
    return myOptionsAndConfirmations.getAllConfirmations();
  }

  @NotNull
  public VcsShowConfirmationOptionImpl getConfirmation(VcsConfiguration.StandardConfirmation option) {
    return myOptionsAndConfirmations.getConfirmation(option);
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
    // in fact, the condition is "should not be called under ApplicationManager.invokeLater() and similiar"
    assert ! ApplicationManager.getApplication().isDispatchThread();
    LOG.assertTrue(myBackgroundOperationCounter > 0, "myBackgroundOperationCounter > 0");
    myBackgroundOperationCounter--;
  }

  public boolean isBackgroundVcsOperationRunning() {
    return myBackgroundOperationCounter > 0;
  }

  public VirtualFile[] getRootsUnderVcs(AbstractVcs vcs) {
    return myMappingsToRoots.getRootsUnderVcs(vcs);
  }

  public VirtualFile[] getAllVersionedRoots() {
    List<VirtualFile> vFiles = new ArrayList<VirtualFile>();
    final AbstractVcs[] vcses = myMappings.getActiveVcses();
    for (AbstractVcs vcs : vcses) {
      Collections.addAll(vFiles, getRootsUnderVcs(vcs));
    }
    return vFiles.toArray(new VirtualFile[vFiles.size()]);
  }

  @NotNull
  public VcsRoot[] getAllVcsRoots() {
    List<VcsRoot> vcsRoots = new ArrayList<VcsRoot>();
    final AbstractVcs[] vcses = myMappings.getActiveVcses();
    for (AbstractVcs vcs : vcses) {
      final VirtualFile[] roots = getRootsUnderVcs(vcs);
      for(VirtualFile root: roots) {
        vcsRoots.add(new VcsRoot(vcs, root));
      }
    }
    return vcsRoots.toArray(new VcsRoot[vcsRoots.size()]);
  }

  public void updateActiveVcss() {
    // not needed
  }

  public void notifyDirectoryMappingChanged() {
    myEventDispatcher.getMulticaster().directoryMappingChanged();
  }

  public void readDirectoryMappings(final Element element) {
    myMappings.clear();

    final List<VcsDirectoryMapping> mappingsList = new ArrayList<VcsDirectoryMapping>();
    final List list = element.getChildren(ELEMENT_MAPPING);
    boolean haveNonEmptyMappings = false;
    for(Object childObj: list) {
      Element child = (Element) childObj;
      final String vcs = child.getAttributeValue(ATTRIBUTE_VCS);
      if (vcs != null && vcs.length() > 0) {
        haveNonEmptyMappings = true;
      }
      VcsDirectoryMapping mapping = new VcsDirectoryMapping(child.getAttributeValue(ATTRIBUTE_DIRECTORY), vcs);
      mappingsList.add(mapping);

      Element rootSettingsElement = child.getChild(ELEMENT_ROOT_SETTINGS);
      if (rootSettingsElement != null) {
        String className = rootSettingsElement.getAttributeValue(ATTRIBUTE_CLASS);
        AbstractVcs vcsInstance = findVcsByName(mapping.getVcs());
        if (vcsInstance != null && className != null) {
          try {
            final Class<?> aClass = vcsInstance.getClass().getClassLoader().loadClass(className);
            final VcsRootSettings instance = (VcsRootSettings) aClass.newInstance();
            instance.readExternal(rootSettingsElement);
            mapping.setRootSettings(instance);
          }
          catch (Exception e) {
            LOG.error("Failed to load VCS root settings class "+ className + " for VCS " + vcsInstance.getClass().getName(), e);
          }
        }
      }
    }
    boolean defaultProject = Boolean.TRUE.toString().equals(element.getAttributeValue(ATTRIBUTE_DEFAULT_PROJECT));
    // run autodetection if there's no VCS in default project and 
    if (haveNonEmptyMappings || !defaultProject) {
      myMappingsLoaded = true;
    }
    StartupManager.getInstance(myProject).registerStartupActivity(new Runnable() {
      public void run() {
        myMappings.setDirectoryMappings(mappingsList);
      }
    });
  }

  public void writeDirectoryMappings(final Element element) {
    if (myProject.isDefault()) {
      element.setAttribute(ATTRIBUTE_DEFAULT_PROJECT, Boolean.TRUE.toString());
    }
    for(VcsDirectoryMapping mapping: getDirectoryMappings()) {
      Element child = new Element(ELEMENT_MAPPING);
      child.setAttribute(ATTRIBUTE_DIRECTORY, mapping.getDirectory());
      child.setAttribute(ATTRIBUTE_VCS, mapping.getVcs());
      final VcsRootSettings rootSettings = mapping.getRootSettings();
      if (rootSettings != null) {
        Element rootSettingsElement = new Element(ELEMENT_ROOT_SETTINGS);
        rootSettingsElement.setAttribute(ATTRIBUTE_CLASS, rootSettings.getClass().getName());
        try {
          rootSettings.writeExternal(rootSettingsElement);
          child.addContent(rootSettingsElement);
        }
        catch (WriteExternalException e) {
          // don't add element
        }
      }
      element.addContent(child);
    }
  }

  public boolean needAutodetectMappings() {
    return !myHaveLegacyVcsConfiguration && !myMappingsLoaded;
  }

  @Nullable
  public AbstractVcs findVersioningVcs(VirtualFile file) {
    for(AbstractVcs vcs: getAllVcss()) {
      if (vcs.isVersionedDirectory(file)) {
        return vcs;
      }
    }
    return null;
  }

  public CheckoutProvider.Listener getCompositeCheckoutListener() {
    return new CompositeCheckoutListener(myProject);
  }

  public void fireDirectoryMappingsChanged() {
    if (! myIsDisposed) {
      myMappings.mappingsChanged();
    }
  }

  public String haveDefaultMapping() {
    return myMappings.haveDefaultMapping();
  }

  public BackgroundableActionEnabledHandler getBackgroundableActionHandler(final MyBackgroundableActions action) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    BackgroundableActionEnabledHandler result = myBackgroundableActionHandlerMap.get(action);
    if (result == null) {
      result = new BackgroundableActionEnabledHandler();
      myBackgroundableActionHandlerMap.put(action, result);
    }
    return result;
  }

  public static enum MyBackgroundableActions {
    ANNOTATE
  }
}
