package com.intellij.openapi.components.impl.stores;

import com.intellij.CommonBundle;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.convert.ProjectConversionHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.OrderedSet;
import com.intellij.util.io.fs.FileSystem;
import com.intellij.util.io.fs.IFile;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

class ProjectStoreImpl extends BaseFileConfigurableStoreImpl implements IProjectStore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.ProjectStoreImpl");
  @NonNls private static final String OLD_PROJECT_SUFFIX = "_old.";
  @NonNls private static final String WORKSPACE_EXTENSION = ".iws";
  @NonNls static final String OPTION_WORKSPACE = "workspace";
  @NonNls public static final String DIRECTORY_STORE_FOLDER = ".idea";

  private ProjectEx myProject;

  @NonNls static final String PROJECT_FILE_MACRO = "PROJECT_FILE";
  @NonNls static final String WS_FILE_MACRO = "WORKSPACE_FILE";
  @NonNls private static final String PROJECT_CONFIG_DIR = "PROJECT_CONFIG_DIR";

  @NonNls private static final String NAME_ATTR = "name";
  @NonNls public static final String USED_MACROS_ELEMENT_NAME = "UsedPathMacros";
  @NonNls public static final String ELEMENT_MACRO = "macro";
  static final String PROJECT_FILE_STORAGE = "$" + PROJECT_FILE_MACRO + "$";
  static final String WS_FILE_STORAGE = "$" + WS_FILE_MACRO + "$";
  static final String DEFAULT_STATE_STORAGE = PROJECT_FILE_STORAGE;
  private StorageScheme myScheme = StorageScheme.DEFAULT;

  @SuppressWarnings({"UnusedDeclaration"})
  public ProjectStoreImpl(final ProjectEx project) {
    super(project);
    myProject = project;
  }

  private static String[] readUsedMacros(Element root) {
    Element child = root.getChild(USED_MACROS_ELEMENT_NAME);
    if (child == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    final List children = child.getChildren(ELEMENT_MACRO);
    final List<String> macroNames = new ArrayList<String>(children.size());
    for (final Object aChildren : children) {
      final Element macro = (Element)aChildren;
      String macroName = macro.getAttributeValue(BaseFileConfigurableStoreImpl.ATTRIBUTE_NAME);
      if (macroName != null) {
        macroNames.add(macroName);
      }
    }
    return macroNames.toArray(new String[macroNames.size()]);
  }

  public boolean checkVersion() {
    int version = getOriginalVersion();
    final ApplicationNamesInfo appNamesInfo = ApplicationNamesInfo.getInstance();
    if (version >= 0 && version < ProjectManagerImpl.CURRENT_FORMAT_VERSION) {
      final VirtualFile projectFile = getProjectFile();
      LOG.assertTrue(projectFile != null);
      String name = projectFile.getNameWithoutExtension();

      String message = ProjectBundle.message("project.convert.old.prompt", projectFile.getName(),
                                             appNamesInfo.getProductName(),
                                             name + OLD_PROJECT_SUFFIX + projectFile.getExtension());
      if (Messages.showYesNoDialog(message, CommonBundle.getWarningTitle(), Messages.getWarningIcon()) != 0) return false;

      final ArrayList<String> conversionProblems = getConversionProblemsStorage();
      if (conversionProblems != null && conversionProblems.size() > 0) {
        StringBuffer buffer = new StringBuffer();
        buffer.append(ProjectBundle.message("project.convert.problems.detected"));
        for (String s : conversionProblems) {
          buffer.append('\n');
          buffer.append(s);
        }
        buffer.append(ProjectBundle.message("project.convert.problems.help"));
        final int result = Messages.showDialog(myProject, buffer.toString(), ProjectBundle.message("project.convert.problems.title"),
                                               new String[]{ProjectBundle.message("project.convert.problems.help.button"),
                                                 CommonBundle.getCloseButtonText()}, 0, Messages.getWarningIcon());
        if (result == 0) {
          HelpManager.getInstance().invokeHelp("project.migrationProblems");
        }
      }

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            VirtualFile projectDir = projectFile.getParent();
            assert projectDir != null;

            backup(projectDir, projectFile);

            VirtualFile workspaceFile = getWorkspaceFile();
            if (workspaceFile != null) {
              backup(projectDir, workspaceFile);
            }
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }

        private void backup(final VirtualFile projectDir, final VirtualFile vile) throws IOException {
          final String oldName = vile.getNameWithoutExtension() + OLD_PROJECT_SUFFIX + vile.getExtension();
          VirtualFile oldFile = projectDir.findOrCreateChildData(this, oldName);
          VfsUtil.saveText(oldFile, VfsUtil.loadText(vile));
        }

      });
    }

    if (version > ProjectManagerImpl.CURRENT_FORMAT_VERSION) {
      String message =
        ProjectBundle.message("project.load.new.version.warning", myProject.getName(), appNamesInfo.getProductName());

      if (Messages.showYesNoDialog(message, CommonBundle.getWarningTitle(), Messages.getWarningIcon()) != 0) return false;
    }

    return true;
  }

  @Override
  protected boolean optimizeTestLoading() {
    return myProject.isOptimiseTestLoadSpeed();
  }

  public void setProjectFilePath(final String filePath) {
    if (filePath != null) {
      final IFile iFile = FileSystem.FILE_SYSTEM.createFile(filePath);
      final IFile dir_store =
        iFile.isDirectory() ? iFile.getChild(DIRECTORY_STORE_FOLDER) : iFile.getParentFile().getChild(DIRECTORY_STORE_FOLDER);

      final StateStorageManager stateStorageManager = getStateStorageManager();
      if (dir_store.exists()) {
        myScheme = StorageScheme.DIRECTORY_BASED;

        stateStorageManager.addMacro(PROJECT_FILE_MACRO, dir_store.getChild("misc.xml").getPath());
        stateStorageManager.addMacro(WS_FILE_MACRO, dir_store.getChild("workspace.xml").getPath());
        stateStorageManager.addMacro(PROJECT_CONFIG_DIR, dir_store.getPath());
      }
      else {
        myScheme = StorageScheme.DEFAULT;
        stateStorageManager.addMacro(PROJECT_FILE_MACRO, filePath);

        if (filePath.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
          String workspacePath =
            filePath.substring(0, filePath.length() - ProjectFileType.DOT_DEFAULT_EXTENSION.length()) + WORKSPACE_EXTENSION;
          stateStorageManager.addMacro(WS_FILE_MACRO, workspacePath);
        }
      }
    }
  }

  @Nullable
  public VirtualFile getProjectBaseDir() {
    final VirtualFile projectFile = getProjectFile();
    if (projectFile != null) return myScheme == StorageScheme.DEFAULT ? projectFile.getParent() : projectFile.getParent().getParent();

    //we are not yet initialized completely
    final StateStorage s = getStateStorageManager().getFileStateStorage(PROJECT_FILE_STORAGE);
    if (!(s instanceof FileBasedStorage)) return null;
    final FileBasedStorage storage = (FileBasedStorage)s;

    final IFile file = storage.getFile();
    if (file == null) return null;

    return LocalFileSystem.getInstance()
      .findFileByIoFile(myScheme == StorageScheme.DEFAULT ? file.getParentFile() : file.getParentFile().getParentFile());
  }

  public void setStorageFormat(final StorageFormat storageFormat) {
  }

  public String getLocation() {
    if (myScheme == StorageScheme.DEFAULT) {
      return getProjectFilePath();
    }
    else {
      return getProjectBaseDir().getPath();
    }
  }

  @NotNull
  public String getProjectName() {
    if (myScheme == StorageScheme.DIRECTORY_BASED) {
      final VirtualFile baseDir = getProjectBaseDir();
      assert baseDir != null;
      return baseDir.getName();
    }

    String temp = getProjectFileName();
    if (temp.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
      temp = temp.substring(0, temp.length() - ProjectFileType.DOT_DEFAULT_EXTENSION.length());
    }
    final int i = temp.lastIndexOf(File.separatorChar);
    if (i >= 0) {
      temp = temp.substring(i + 1, temp.length() - i + 1);
    }
    return temp;
  }

  @Nullable
  public String getPresentableUrl() {
    if (myScheme == StorageScheme.DIRECTORY_BASED) {
      final VirtualFile baseDir = getProjectBaseDir();
      return baseDir != null ? baseDir.getPresentableUrl() : null;
    }

    final VirtualFile projectFile = getProjectFile();
    return projectFile != null ? projectFile.getPresentableUrl() : null;
  }

  public boolean reload() {
    System.out.println("***** reloading: " + myProject);
    return false;
  }


  public void loadProject() throws IOException, JDOMException, InvalidDataException, StateStorage.StateStorageException {
    load();
    myProject.init();
  }

  @Nullable
  private static ProjectConversionHelper getConversionHelper(Project project) {
    return (ProjectConversionHelper)project.getPicoContainer().getComponentInstance(ProjectConversionHelper.class);
  }

  private static boolean checkMacros(final Project project, Element root) throws IOException {
    final Set<String> usedMacros = new HashSet<String>(Arrays.asList(readUsedMacros(root)));

    usedMacros.removeAll(getDefinedMacros());

    // try to lookup values in System properties
    @NonNls final String pathMacroSystemPrefix = "path.macro.";
    for (Iterator it = usedMacros.iterator(); it.hasNext();) {
      final String macro = (String)it.next();
      final String value = System.getProperty(pathMacroSystemPrefix + macro, null);
      if (value != null) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            PathMacrosImpl.getInstanceEx().setMacro(macro, value);
          }
        });
        it.remove();
      }
    }

    if (usedMacros.isEmpty()) {
      return true; // all macros in configuration files are defined
    }

    // there are undefined macros, need to define them before loading components
    return ProjectManagerImpl.showMacrosConfigurationDialog(project, usedMacros);
  }

  private static Set<String> getDefinedMacros() {
    final PathMacros pathMacros = PathMacros.getInstance();

    Set<String> definedMacros = new HashSet<String>(pathMacros.getUserMacroNames());
    definedMacros.addAll(pathMacros.getSystemMacroNames());
    definedMacros = Collections.unmodifiableSet(definedMacros);
    return definedMacros;
  }


  @Nullable
  public VirtualFile getProjectFile() {
    if (myProject.isDefault()) return null;
    final FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getFileStateStorage(PROJECT_FILE_STORAGE);
    assert storage != null;
    return storage.getVirtualFile();
  }

  @Nullable
  public VirtualFile getWorkspaceFile() {
    if (myProject.isDefault()) return null;
    final FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getFileStateStorage(WS_FILE_STORAGE);
    assert storage != null;
    return storage.getVirtualFile();
  }

  public void loadProjectFromTemplate(final ProjectImpl defaultProject) {
    final StateStorage stateStorage = getStateStorageManager().getFileStateStorage(DEFAULT_STATE_STORAGE);

    assert stateStorage instanceof XmlElementStorage;
    XmlElementStorage xmlElementStorage = (XmlElementStorage)stateStorage;

    defaultProject.save();
    final IProjectStore projectStore = defaultProject.getStateStore();
    assert projectStore instanceof DefaultProjectStoreImpl;
    DefaultProjectStoreImpl defaultProjectStore = (DefaultProjectStoreImpl)projectStore;
    final Element element = defaultProjectStore.getStateCopy();
    if (element != null) {
      xmlElementStorage.setDefaultState(element);
    }
  }

  @NotNull
  public String getProjectFileName() {
    final FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getFileStateStorage(PROJECT_FILE_STORAGE);
    assert storage != null;
    return storage.getFileName();
  }

  @NotNull
  public String getProjectFilePath() {
    if (myProject.isDefault()) return "";

    final FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getFileStateStorage(PROJECT_FILE_STORAGE);
    assert storage != null;
    return storage.getFilePath();
  }

  protected XmlElementStorage getMainStorage() {
    final XmlElementStorage storage = (XmlElementStorage)getStateStorageManager().getFileStateStorage(DEFAULT_STATE_STORAGE);
    assert storage != null;
    return storage;
  }


  public List<VirtualFile> getAllStorageFiles(final boolean includingSubStructures) {
    final List<VirtualFile> result = super.getAllStorageFiles(includingSubStructures);

    if (includingSubStructures) {
      for (Module module : getPersistentModules()) {
        result.addAll(((ModuleImpl)module).getStateStore().getAllStorageFiles(includingSubStructures));
      }
    }

    return result;
  }


  protected StateStorageManager createStateStorageManager() {
    return new ProjectStateStorageManager(PathMacroManager.getInstance(getComponentManager()).createTrackingSubstitutor(), myProject);
  }


  static class  ProjectStorageData extends BaseStorageData {
    public ProjectStorageData(final String rootElementName) {
      super(rootElementName);
    }

    protected ProjectStorageData(ProjectStorageData storageData) {
      super(storageData);
    }

    protected void load(@NotNull final Element rootElement) throws IOException {
      super.load(rootElement);
    }

    @NotNull
    protected Element save() {
      return super.save();
    }

    public XmlElementStorage.StorageData clone() {
      return new ProjectStorageData(this);
    }
  }

  static class IprStorageData extends ProjectStorageData {
    private final Set<String> myUsedMacros;
    private final Project myProject;

    public IprStorageData(final String rootElementName, Project project) {
      super(rootElementName);
      myProject = project;
      myUsedMacros = new TreeSet<String>();
    }

    public IprStorageData(final IprStorageData storageData) {
      super(storageData);
      myUsedMacros = new TreeSet<String>(storageData.myUsedMacros);
      myProject = storageData.myProject;
    }

    protected void load(@NotNull final Element rootElement) throws IOException {
      final boolean macrosOk = checkMacros(myProject, rootElement);
      if (!macrosOk) {
        throw new IOException(ProjectBundle.message("project.load.undefined.path.variables.error"));
      }

      super.load(rootElement);
    }

    @NotNull
    protected Element save() {
      final Element root = super.save();
      final ProjectConversionHelper conversionHelper = getConversionHelper(myProject);

      if (conversionHelper != null) {
        conversionHelper.convertWorkspaceRootToOldFormat(root);
      }

      final PathMacros pathMacros = PathMacros.getInstance();
      final Set<String> systemMacroNames = pathMacros.getSystemMacroNames();

      for (Iterator<String> i = myUsedMacros.iterator(); i.hasNext();) {
        String macro = i.next();

        for (String systemMacroName : systemMacroNames) {
          if (macro.equals(systemMacroName) || macro.indexOf("$" + systemMacroName + "$") >= 0) {
            i.remove();
          }
        }
      }

      root.removeChildren(USED_MACROS_ELEMENT_NAME);

      if (!myUsedMacros.isEmpty()) {
        Element usedMacrosElement = new Element(USED_MACROS_ELEMENT_NAME);

        for (String usedMacro : myUsedMacros) {
          Element macroElement = new Element(ELEMENT_MACRO);

          macroElement.setAttribute(NAME_ATTR, usedMacro);

          usedMacrosElement.addContent(macroElement);
        }

        root.addContent(usedMacrosElement);
      }

      return root;
    }

    public XmlElementStorage.StorageData clone() {
      return new IprStorageData(this);
    }

    protected int computeHash() {
      return super.computeHash()*31 + myUsedMacros.hashCode();
    }

    protected void setUsedMacros(Collection<String> m) {
      myUsedMacros.clear();
      myUsedMacros.addAll(m);
      clearHash();
    }
  }

  protected SaveSessionImpl createSaveSession() throws StateStorage.StateStorageException {
    return new ProjectSaveSession();
  }

  private class ProjectSaveSession extends SaveSessionImpl {
    List<SaveSession> myModuleSaveSessions = new ArrayList<SaveSession>();

    public ProjectSaveSession() throws StateStorage.StateStorageException {
      try {
        for (Module module : getPersistentModules()) {
          myModuleSaveSessions.add(((ModuleImpl)module).getStateStore().startSave());
        }
      }
      catch (IOException e) {
        throw new StateStorage.StateStorageException(e);
      }
    }

    public Collection<String> getUsedMacros() throws StateStorage.StateStorageException {
      Set<String> result = new HashSet<String>(super.getUsedMacros());

      for (SaveSession moduleSaveSession : myModuleSaveSessions) {
        result.addAll(moduleSaveSession.getUsedMacros());
      }

      return result;
    }

    public List<VirtualFile> getAllStorageFilesToSave(final boolean includingSubStructures) throws IOException {
      if (!includingSubStructures) return super.getAllStorageFilesToSave(false);

      List<VirtualFile> result = new ArrayList<VirtualFile>(super.getAllStorageFilesToSave(false));

      for (SaveSession moduleSaveSession : myModuleSaveSessions) {
        result.addAll(moduleSaveSession.getAllStorageFilesToSave(true));
      }

      return result;
    }

    public SaveSession save() throws IOException {
      final ReadonlyStatusHandler.OperationStatus operationStatus = ensureConfigFilesWritable();
      if (operationStatus == null) {
        throw new IOException();
      }
      else if (operationStatus.hasReadonlyFiles()) {
        MessagesEx.error(myProject, ProjectBundle.message("project.save.error", operationStatus.getReadonlyFilesMessage())).showLater();
        throw new SaveCancelledException();
      }

      for (SaveSession moduleSaveSession : myModuleSaveSessions) {
        moduleSaveSession.save();
      }

      super.save();

      return this;
    }

    protected void commit() throws StateStorage.StateStorageException {
      super.commit();

      //todo: make it clearer
      final XmlElementStorage.MySaveSession session = (XmlElementStorage.MySaveSession)mySaveSession.getSaveSession(DEFAULT_STATE_STORAGE);
      final XmlElementStorage.StorageData data = session.getData();

      if (data instanceof IprStorageData) {
        final IprStorageData storageData = (IprStorageData)data;

        storageData.setUsedMacros(getUsedMacros());
      }
    }

    public void finishSave() {
      for (SaveSession moduleSaveSession : myModuleSaveSessions) {
        moduleSaveSession.finishSave();
      }

      super.finishSave();
    }

    private ReadonlyStatusHandler.OperationStatus ensureConfigFilesWritable() {
      return ApplicationManager.getApplication().runWriteAction(new Computable<ReadonlyStatusHandler.OperationStatus>() {
        public ReadonlyStatusHandler.OperationStatus compute() {
          final List<VirtualFile> filesToSave;
          try {
            filesToSave = getAllStorageFilesToSave(true);
          }
          catch (IOException e) {
            LOG.error(e);
            return null;
          }

          List<VirtualFile> readonlyFiles = new ArrayList<VirtualFile>();
          for (VirtualFile file : filesToSave) {
            if (!file.isWritable()) readonlyFiles.add(file);
          }

          if (readonlyFiles.isEmpty()) return new ReadonlyStatusHandler.OperationStatus(VirtualFile.EMPTY_ARRAY, VirtualFile.EMPTY_ARRAY);

          return ReadonlyStatusHandler.getInstance(myProject)
            .ensureFilesWritable(readonlyFiles.toArray(new VirtualFile[readonlyFiles.size()]));
        }
      });
    }
  }

  private Module[] getPersistentModules() {
    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    if (moduleManager instanceof ModuleManagerImpl) {
      return ((ModuleManagerImpl)moduleManager).getPersistentModules();
    }
    else {
      return moduleManager.getModules();
    }
  }

  private StateStorageChooser myStateStorageChooser = new StateStorageChooser() {
    public Storage[] selectStorages(final Storage[] storages, final Object component, final StateStorageOperation operation) {
      if (operation == StateStorageOperation.READ) {
        OrderedSet<Storage> result = new OrderedSet<Storage>();

        for (Storage storage : storages) {
          if (storage.scheme() == myScheme) {
            result.add(0, storage);
          }
        }

        for (Storage storage : storages) {
          if (storage.scheme() == StorageScheme.DEFAULT) {
            result.add(storage);
          }
        }

        return result.toArray(new Storage[result.size()]);
      }
      else if (operation == StateStorageOperation.WRITE) {
        List<Storage> result = new ArrayList<Storage>();
        for (Storage storage : storages) {
          if (storage.scheme() == myScheme) {
            result.add(storage);
          }
        }

        if (!result.isEmpty()) return result.toArray(new Storage[result.size()]);

        for (Storage storage : storages) {
          if (storage.scheme() == StorageScheme.DEFAULT) {
            result.add(storage);
          }
        }

        return result.toArray(new Storage[result.size()]);
      }

      return new Storage[]{};
    }
  };

  @Nullable
  protected StateStorageChooser getDefaultStateStorageChooser() {
    return myStateStorageChooser;
  }
}

