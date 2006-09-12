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
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vcs.checkin.CodeAnalysisBeforeCheckinHandler;
import com.intellij.openapi.vcs.checkin.StandardBeforeCheckinHandler;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.fileView.impl.VirtualAndPsiFileDataProvider;
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
import com.intellij.util.EventDispatcher;
import com.intellij.util.Icons;
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

  private boolean myIsDisposed = false;

  private ContentManager myContentManager;
  private EditorAdapter myEditorAdapter;

  private boolean myIsBeforeProjectStarted = true;
  @NonNls private static final String OPTIONS_SETTING = "OptionsSetting";
  @NonNls private static final String CONFIRMATIONS_SETTING = "ConfirmationsSetting";
  @NonNls private static final String VALUE_ATTTIBUTE = "value";
  @NonNls private static final String ID_ATTRIBUTE = "id";
  private final List<CheckinHandlerFactory> myRegisteredBeforeCheckinHandlers = new ArrayList<CheckinHandlerFactory>();
  private boolean myHaveEmptyContentRevisions = true;
  private EventDispatcher<VcsListener> myEventDispatcher = EventDispatcher.create(VcsListener.class);

  private volatile int myBackgroundOperationCounter = 0;

  public ProjectLevelVcsManagerImpl(Project project) {
    this(project, new AbstractVcs[0]);
  }

  public ProjectLevelVcsManagerImpl(Project project, AbstractVcs[] vcses) {
    myProject = project;
    myVcss = new ArrayList<AbstractVcs>(Arrays.asList(vcses));
  }

  private final Map<String, VcsShowOptionsSettingImpl> myOptions = new LinkedHashMap<String, VcsShowOptionsSettingImpl>();
  private final Map<String, VcsShowConfirmationOptionImpl> myConfirmations = new LinkedHashMap<String, VcsShowConfirmationOptionImpl>();

  public void initComponent() {
    createSettingFor(VcsConfiguration.StandardOption.ADD);
    createSettingFor(VcsConfiguration.StandardOption.REMOVE);
    createSettingFor(VcsConfiguration.StandardOption.CHECKIN);
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

  public AbstractVcs findVcsByName(String name) {
    if (name == null) return null;

    final AbstractVcs[] allActiveVcss = getAllVcss();
    for (AbstractVcs vcs : allActiveVcss) {
      if (vcs.getName().equals(name)) {
        return vcs;
      }

    }

    return null;
  }

  public AbstractVcs[] getAllVcss() {
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

    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        myIsBeforeProjectStarted = false;
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
        if (toolWindowManager != null) { // Can be null in tests
          ToolWindow toolWindow =
            toolWindowManager.registerToolWindow(ToolWindowId.VCS, myContentManager.getComponent(), ToolWindowAnchor.BOTTOM);
          toolWindow.setIcon(Icons.VCS_SMALL_TAB);
          toolWindow.installWatcher(myContentManager);
        }
      }
    });
  }

  public void initialize() {
    final AbstractVcs[] abstractVcses = myVcss.toArray(new AbstractVcs[myVcss.size()]);
    for (AbstractVcs abstractVcse : abstractVcses) {
      registerVcs(abstractVcse);
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
      if (ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file) != abstractVcs) {
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
    try {
      vcs.shutdown();
    }
    catch (VcsException e) {
      LOG.info(e);
    }
    myVcss.remove(vcs);
    myCachedVCSs = null;
  }

  private Project getProject() {
    return myProject;
  }

  public ContentManager getContentManager() {
    return myContentManager;
  }

  @Nullable
  String getBaseVersionContent(final VirtualFile file) {
    final Change change = ChangeListManager.getInstance(myProject).getChange(file);
    if (change != null) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      if (beforeRevision != null) {
        final String content = beforeRevision.getContent();
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
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      if (ModuleLevelVcsManager.getInstance(module).getActiveVcs() == vcs) return true;
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
    for (Module module : modules) {
      if (ModuleLevelVcsManager.getInstance(module).getActiveVcs() == vcs) {
        result.add(module);
      }
    }
    return result.toArray(new Module[result.size()]);
  }

  public AbstractVcs[] getAllActiveVcss() {
    ArrayList<AbstractVcs> result = new ArrayList<AbstractVcs>();
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      AbstractVcs activeVcs = ModuleLevelVcsManager.getInstance(module).getActiveVcs();
      if (activeVcs != null && !result.contains(activeVcs)) {
        result.add(activeVcs);
      }
    }
    return result.toArray(new AbstractVcs[result.size()]);

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
    LOG.assertTrue(myIsBeforeProjectStarted, "getStandardOption should be called from projectOpened only");
    final VcsShowOptionsSettingImpl options = myOptions.get(option.getId());
    options.addApplicableVcs(vcs);
    return options;
  }

  @NotNull
  public VcsShowSettingOption getOrCreateCustomOption(@NotNull String vcsActionName, @NotNull AbstractVcs vcs) {
    LOG.assertTrue(myIsBeforeProjectStarted, "getOrCreateCustomOption should be called from projectOpened only");
    final VcsShowOptionsSettingImpl option = getOrCreateOption(vcsActionName);
    option.addApplicableVcs(vcs);
    return option;
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
    LOG.assertTrue(myIsBeforeProjectStarted, "VcsShowConfirmationOption should be called from projectOpened only");
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

  public void notifyModuleVcsChanged(Module module, AbstractVcs newVcs) {
    myEventDispatcher.getMulticaster().moduleVcsChanged(module, newVcs);
  }

  boolean hasEmptyContentRevisions() {
    return myHaveEmptyContentRevisions;
  }

  void resetHaveEmptyContentRevisions() {
    myHaveEmptyContentRevisions = false;
  }
}