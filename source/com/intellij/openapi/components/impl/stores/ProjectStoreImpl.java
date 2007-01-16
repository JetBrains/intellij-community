package com.intellij.openapi.components.impl.stores;

import com.intellij.CommonBundle;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
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
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.vfs.LocalFileSystem;
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
import java.io.InputStream;
import java.util.*;

class ProjectStoreImpl extends BaseFileConfigurableStoreImpl implements IProjectStore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.ProjectStoreImpl");
  @NonNls private static final String OLD_PROJECT_SUFFIX = "_old.";
  @NonNls private static final String WORKSPACE_EXTENSION = ".iws";
  @NonNls private static final String OPTION_WORKSPACE = "workspace";

  private ProjectImpl myProject;
  private ProjectManagerImpl myProjectManager;
  private static boolean ourSaveSettingsInProgress = false;

  @Nullable
  private ConfigurationFile myProjectFile;
  @Nullable
  private ConfigurationFile myWorkspaceFile;

  private String myFilePath;

  public ProjectStoreImpl(final ComponentManagerImpl componentManager, final ProjectImpl project, final ProjectManagerImpl projectManager) {
    super(componentManager);
    myProject = project;
    myProjectManager = projectManager;
  }

  private static String[] readUsedMacros(Element root) {
    Element child = root.getChild(ConfigurationFile.USED_MACROS_ELEMENT_NAME);
    if (child == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    final List children = child.getChildren(ConfigurationFile.ELEMENT_MACRO);
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

  @Nullable
  protected Element getDefaults(final Object component) throws JDOMException, IOException, InvalidDataException {

    //[max | mike] speed optimization. If you need default initialization in tests
    //use field initializers instead.

    if (myProject.myOptimiseTestLoadSpeed) return null;

    InputStream stream = DecodeDefaultsUtil.getDefaultsInputStream(component);
    if (stream != null) {
      Document document = JDOMUtil.loadDocument(stream);
      stream.close();

      if (document == null) {
        throw new InvalidDataException();
      }
      Element root = document.getRootElement();
      if (root == null || !BaseFileConfigurableStoreImpl.ELEMENT_COMPONENT.equals(root.getName())) {
        throw new InvalidDataException();
      }

      PathMacroManager.getInstance(myProject).expandPaths(root);
      return root;
    }
    return null;
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
    final List<VirtualFile> readonlyFiles = new ArrayList<VirtualFile>();
    collectFileNeedsToBeWritten(getConfigurationFiles(), readonlyFiles);
    return ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(readonlyFiles.toArray(new VirtualFile[readonlyFiles.size()]));
  }

  public void saveProject() {
    try {
      if (!ourSaveSettingsInProgress) {
        try {
          ourSaveSettingsInProgress = true;
          PathMacroManager.startLoggingUsedMacros();

          myProject.saveSettingsSavingComponents();

          final Module[] modules = ModuleManager.getInstance(myProject).getModules();
          final ReadonlyStatusHandler.OperationStatus operationStatus = ensureConfigFilesWritable();
          if (operationStatus.hasReadonlyFiles()) {
            MessagesEx.error(myProject, ProjectBundle.message("project.save.error", operationStatus.getReadonlyFilesMessage())).showLater();
            return;
          }

          final Exception[] exception = new Exception[]{null};
          CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
            public void run() {
              for (Module module : modules) {
                try {
                  ((ModuleImpl)module).getStateStore().save();
                }
                catch (Exception e) {
                  exception[0] = e;
                  return;
                }
              }
              try {
                // important! must save project after modules are saved, otherwise not all used macros will be saved to the ipr file
                save();
              }
              catch (Exception e) {
                exception[0] = e;
              }
            }
          }, ProjectBundle.message("project.save.settings.command"), null);

          if (exception[0] != null) {
            LOG.info(exception[0]);
            MessagesEx.error(myProject, ProjectBundle.message("project.save.error", exception[0].getMessage())).showLater();
          }
        }
        finally {
          PathMacroManager.endLoggingUsedMacros();
          ourSaveSettingsInProgress = false;
        }
      }
    }
    finally {
      ShutDownTracker.getInstance().unregisterStopperThread(Thread.currentThread());
    }
  }

  void initJdomExternalizable(final Class componentClass, final BaseComponent component) {
    if (myProject.myOptimiseTestLoadSpeed) return; //test load speed optimization
    super.initJdomExternalizable(component);
  }

  public void setProjectFilePath(final String filePath) {
    myFilePath = filePath;
    if (filePath != null) {
      myProjectFile = new ConfigurationFile(filePath);
      if (filePath.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
        String workspacePath = filePath.substring(0, filePath.length() - ProjectFileType.DOT_DEFAULT_EXTENSION.length()) + WORKSPACE_EXTENSION;
        myWorkspaceFile = new ConfigurationFile(workspacePath);
      }
      else {
        myWorkspaceFile = null;
      }
    }
    else {
      myProjectFile = null;
      myWorkspaceFile = null;
    }
  }

  @Nullable
  public VirtualFile getProjectBaseDir() {
    final VirtualFile projectFile = getProjectFile();
    return projectFile != null ? projectFile.getParent() : null;
  }


  public void loadProject() throws IOException, JDOMException, InvalidDataException {
    // http://www.jetbrains.net/jira/browse/IDEA-1556. Enforce refresh. Project files may potentially reside in non-yet valid vfs paths.
    final ConfigurationFile[] files = getConfigurationFiles();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (ConfigurationFile file : files) {
          LocalFileSystem.getInstance().refreshAndFindFileByPath(file.getFilePath().replace(File.separatorChar, '/'));
        }
      }
    });

    final boolean macrosOk = checkMacros(myProjectManager, getDefinedMacros(myProjectManager));
    if (!macrosOk) {
      throw new IOException(ProjectBundle.message("project.load.undefined.path.variables.error"));
    }

    myProject.getStateStore().loadSavedConfiguration();

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
            projectManager.myPathMacros.setMacro(macro, value);
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

  private static Set<String> getDefinedMacros(ProjectManagerImpl projectManager) {
    Set<String> definedMacros = new HashSet<String>(projectManager.myPathMacros.getUserMacroNames());
    definedMacros.addAll(projectManager.myPathMacros.getSystemMacroNames());
    definedMacros = Collections.unmodifiableSet(definedMacros);
    return definedMacros;
  }


  @Nullable
  public VirtualFile getProjectFile() {
    return myProjectFile != null ? myProjectFile.getVirtualFile() : null;
  }

  @Nullable
  public VirtualFile getWorkspaceFile() {
    return myWorkspaceFile != null ? myWorkspaceFile.getVirtualFile() : null;
  }

  public void loadProjectFromTemplate(final Project project) {
    ProjectImpl defaultProject = (ProjectImpl)project;
    Element element = defaultProject.getStateStore().saveToXml(defaultProject.getStateStore().getProjectFile());
    try {
      loadFromXml(element, myFilePath);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @NotNull
  public String getProjectFileName() {
    return myProjectFile == null ? "" : myProjectFile.getFileName();
  }

  @NotNull
  public String getProjectFilePath() {
    return myProjectFile == null ? "" : myProjectFile.getFilePath();
  }


  protected ConfigurationFile[] getConfigurationFiles() {
    final List<ConfigurationFile> files = new ArrayList<ConfigurationFile>(2);

    if (myProjectFile != null) {
      files.add(myProjectFile);
    }

    if (myWorkspaceFile != null) {
      files.add(myWorkspaceFile);
    }
    return files.toArray(new ConfigurationFile[files.size()]);
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


  public synchronized boolean isSavePathsRelative() {
    return super.isSavePathsRelative();
  }

  synchronized String getLineSeparator(final VirtualFile file) {
    return FileDocumentManager.getInstance().getLineSeparator(file, myProject);
  }

  public synchronized void initStore() {
    getComponentManager().initComponentsFromExtensions(Extensions.getArea(myProject));
  }


  void collectFileNeedsToBeWritten(final ConfigurationFile[] files, final List<VirtualFile> readonlyFiles) {
    super.collectFileNeedsToBeWritten(files, readonlyFiles);

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      final ModuleStoreImpl moduleStore = (ModuleStoreImpl)((ModuleImpl)module).getStateStore();
      moduleStore.collectFileNeedsToBeWritten(moduleStore.getConfigurationFiles(), readonlyFiles);
    }
  }


  public List<VirtualFile> getAllStorageFiles(final boolean includingSubStructures) {
    final List<VirtualFile> result = super.getAllStorageFiles(includingSubStructures);

    if (includingSubStructures) {
      ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      final Module[] modules = moduleManager.getModules();
      for (Module module : modules) {
        result.addAll(((ModuleImpl)module).getStateStore().getAllStorageFiles(true));
      }
    }

    return result;
  }
}

