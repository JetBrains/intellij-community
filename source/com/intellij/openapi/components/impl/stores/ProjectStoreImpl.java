package com.intellij.openapi.components.impl.stores;

import com.intellij.CommonBundle;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jdom.Document;
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
  @NonNls private static final String OPTION_WORKSPACE = "workspace";

  private ProjectImpl myProject;
  private ProjectManagerImpl myProjectManager;

  @NonNls private static final String PROJECT_FILE_MACRO = "PROJECT_FILE";
  @NonNls private static final String WS_FILE_MACRO = "WORKSPACE_FILE";

  static final String PROJECT_FILE_STORAGE = "$" + PROJECT_FILE_MACRO + "$";
  static final String WS_FILE_STORAGE = "$" + WS_FILE_MACRO + "$";
  static final String DEFAULT_STATE_STORAGE = PROJECT_FILE_STORAGE;

  @SuppressWarnings({"UnusedDeclaration"})
  public ProjectStoreImpl(final ComponentManagerImpl componentManager, final ProjectImpl project, final ProjectManagerImpl projectManager) {
    super(componentManager);
    myProject = project;
    myProjectManager = projectManager;
  }

  private static String[] readUsedMacros(Element root) {
    Element child = root.getChild(ProjectStateStorageManager.USED_MACROS_ELEMENT_NAME);
    if (child == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    final List children = child.getChildren(ProjectStateStorageManager.ELEMENT_MACRO);
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
    if (version >= 0 && version < ProjectManagerImpl.CURRENT_FORMAT_VERSION) {
      final VirtualFile projectFile = getProjectFile();
      LOG.assertTrue(projectFile != null);
      String name = projectFile.getNameWithoutExtension();

      String message = ProjectBundle.message("project.convert.old.prompt", projectFile.getName(),
                                             ApplicationNamesInfo.getInstance().getProductName(),
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
                                                 CommonBundle.getCloseButtonText()}, 0,
                                                                                     Messages.getWarningIcon()
        );
        if (result == 0) {
          HelpManager.getInstance().invokeHelp("project.migrationProblems");
        }
      }

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            VirtualFile projectDir = projectFile.getParent();
            assert projectDir != null;

            final String oldProjectName = projectFile.getNameWithoutExtension() + OLD_PROJECT_SUFFIX + projectFile.getExtension();
            VirtualFile oldProject = projectDir.findChild(oldProjectName);
            if (oldProject == null) {
              oldProject = projectDir.createChildData(this, oldProjectName);
            }
            VfsUtil.saveText(oldProject, VfsUtil.loadText(projectFile));
            VirtualFile workspaceFile = getWorkspaceFile();
            if (workspaceFile != null) {
              final String oldWorkspaceName = workspaceFile.getNameWithoutExtension() + OLD_PROJECT_SUFFIX +
                                              getWorkspaceFile().getExtension();
              VirtualFile oldWorkspace = projectDir.findChild(oldWorkspaceName);
              if (oldWorkspace == null) {
                oldWorkspace = projectDir.createChildData(this, oldWorkspaceName);
              }
              VfsUtil.saveText(oldWorkspace, VfsUtil.loadText(workspaceFile));
            }
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      });
    }

    if (version > ProjectManagerImpl.CURRENT_FORMAT_VERSION) {
      String message =
        ProjectBundle.message("project.load.new.version.warning", myProject.getName(), ApplicationNamesInfo.getInstance().getProductName());

      if (Messages.showYesNoDialog(message, CommonBundle.getWarningTitle(), Messages.getWarningIcon()) != 0) return false;
    }

    return true;
  }

  private ReadonlyStatusHandler.OperationStatus ensureConfigFilesWritable() {
    return ApplicationManager.getApplication().runWriteAction(new Computable<ReadonlyStatusHandler.OperationStatus>() {
      public ReadonlyStatusHandler.OperationStatus compute() {
        final List<VirtualFile> filesToSave = getAllStorageFilesToSave(true);

        List<VirtualFile> readonlyFiles = new ArrayList<VirtualFile>();
        for (VirtualFile file : filesToSave) {
          if (!file.isWritable()) readonlyFiles.add(file);
        }

        return ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(readonlyFiles.toArray(new VirtualFile[readonlyFiles.size()]));
      }
    });
  }


  @Override
  protected void saveStorageManager() throws IOException {
    final ReadonlyStatusHandler.OperationStatus operationStatus = ensureConfigFilesWritable();
    if (operationStatus.hasReadonlyFiles()) {
      MessagesEx.error(myProject, ProjectBundle.message("project.save.error", operationStatus.getReadonlyFilesMessage())).showLater();
      throw new SaveCancelledException();
    }

    super.saveStorageManager();
  }

  @Override
  public void initComponent(final Object component) {
    if (myProject.myOptimiseTestLoadSpeed) return; //test load speed optimization
    super.initComponent(component);
  }

  public void setProjectFilePath(final String filePath) {
    if (filePath != null) {
      getStateStorageManager().addMacro(PROJECT_FILE_MACRO, filePath);

      if (filePath.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
        String workspacePath = filePath.substring(0, filePath.length() - ProjectFileType.DOT_DEFAULT_EXTENSION.length()) + WORKSPACE_EXTENSION;
        getStateStorageManager().addMacro(WS_FILE_MACRO, workspacePath);
      }
    }
  }

  @Nullable
  public VirtualFile getProjectBaseDir() {
    final VirtualFile projectFile = getProjectFile();
    return projectFile != null ? projectFile.getParent() : null;
  }


  public void loadProject() throws IOException, JDOMException, InvalidDataException {
    final boolean macrosOk = checkMacros(myProjectManager, getDefinedMacros());
    if (!macrosOk) {
      throw new IOException(ProjectBundle.message("project.load.undefined.path.variables.error"));
    }

    load();
    myProject.init();
  }

  private boolean checkMacros(final ProjectManagerImpl projectManager, Set<String> definedMacros) throws IOException, JDOMException {
    String projectFilePath = getProjectFilePath();
    Document document = JDOMUtil.loadDocument(new File(projectFilePath));
    Element root = document.getRootElement();
    final Set<String> usedMacros = new HashSet<String>(Arrays.asList(readUsedMacros(root)));

    usedMacros.removeAll(definedMacros);

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
    return ProjectManagerImpl.showMacrosConfigurationDialog(myProject, usedMacros);
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

    try {
      return ((FileBasedStorage)getStateStorageManager().getStateStorage(PROJECT_FILE_STORAGE)).getVirtualFile();
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  @Nullable
  public VirtualFile getWorkspaceFile() {
    if (myProject.isDefault()) return null;
    
    try {
      return ((FileBasedStorage)getStateStorageManager().getStateStorage(WS_FILE_STORAGE)).getVirtualFile();
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  public void loadProjectFromTemplate(final Project project) {
  }

  @NotNull
  public String getProjectFileName() {
    return ((FileBasedStorage)getStateStorageManager().getStateStorage(PROJECT_FILE_STORAGE)).getFileName();
  }

  @NotNull
  public String getProjectFilePath() {
    if (myProject.isDefault()) return "";

    return ((FileBasedStorage)getStateStorageManager().getStateStorage(PROJECT_FILE_STORAGE)).getFilePath();
  }


  protected XmlElementStorage getMainStorage() {
    return (XmlElementStorage)getStateStorageManager().getStateStorage(DEFAULT_STATE_STORAGE);
  }

  protected VirtualFile getComponentConfigurationFile(ComponentConfig componentConfig) {
    if (myProject.isDefault()) {
      return getProjectFile();
    }

    return isWorkspace(componentConfig.options) ? getWorkspaceFile() : getProjectFile();
  }

  private static boolean isWorkspace(final Map options) {
    return options != null && Boolean.parseBoolean((String)options.get(OPTION_WORKSPACE));
  }

  @NonNls
  protected String getRootNodeName() {
    return "project";
  }


  public boolean isSavePathsRelative() {
    return super.isSavePathsRelative();
  }

  String getLineSeparator(final VirtualFile file) {
    return FileDocumentManager.getInstance().getLineSeparator(file, myProject);
  }

  public void initStore() {
  }

  public List<VirtualFile> getAllStorageFiles(final boolean includingSubStructures) {
    final List<VirtualFile> result = super.getAllStorageFiles(includingSubStructures);

    if (includingSubStructures) {
      ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      final Module[] modules = moduleManager.getModules();
      for (Module module : modules) {
        result.addAll(((ModuleImpl)module).getStateStore().getAllStorageFiles(includingSubStructures));
      }
    }

    return result;
  }


  public List<VirtualFile> getAllStorageFilesToSave(final boolean includingSubStructures) {
    final List<VirtualFile> result = super.getAllStorageFilesToSave(includingSubStructures);

    if (includingSubStructures) {
      ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      final Module[] modules = moduleManager.getModules();
      for (Module module : modules) {
        result.addAll(((ModuleImpl)module).getStateStore().getAllStorageFilesToSave(includingSubStructures));
      }
    }

    return result;
  }

  @Override
  protected StateStorage getOldStorage(final Object component) {
    final ComponentConfig config = getComponentManager().getConfig(component.getClass());
    assert config != null;

    String macro = PROJECT_FILE_MACRO;

    if (isWorkspace(config.options)) {
      macro = WS_FILE_MACRO;
    }
    return getStateStorageManager().getStateStorage("$" + macro + "$");
  }

  protected StateStorageManager createStateStorageManager() {
    return new ProjectStateStorageManager(PathMacroManager.getInstance(getComponentManager()).createTrackingSubstitutor(), myProject);
  }

  public ProjectImpl getProject() {
    return myProject;
  }

  @Override
  public void commit() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    final Module[] modules = moduleManager.getModules();
    for (Module module : modules) {
      ((ModuleImpl)module).getStateStore().commit();
    }

    super.commit();
  }
}

