package com.intellij.openapi.project.impl;

import com.intellij.application.options.ExpandMacroToPathMap;
import com.intellij.application.options.PathMacroMap;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomModel;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;


/**
 *
 */
public class ProjectImpl extends BaseFileConfigurable implements ProjectEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.project.impl.ProjectImpl");

  private ProjectManagerImpl myManager;
  private ConfigurationFile myProjectFile;
  private ConfigurationFile myWorkspaceFile;

  private MyProjectManagerListener myProjectManagerListener;
  private boolean myDummy;

  private ArrayList<String> myConversionProblemsStorage = new ArrayList<String>();

  private static boolean ourSaveSettingsInProgress = false;

  @NonNls private static final String WORKSPACE_EXTENSION = ".iws";
  @NonNls private static final String PROJECT_LAYER = "project-components";
  private boolean myDisposed = false;
  private PomModel myModel = null;

  private final boolean myOptimiseTestLoadSpeed;
  @NonNls private static final String USED_MACROS_ELEMENT_NAME = "UsedPathMacros";
  private static final Comparator<String> ourStringComparator = new Comparator<String>() {
    public int compare(String o1, String o2) {
      return o1.compareTo(o2);
    }
  };
  @NonNls private static final String OPTION_WORKSPACE = "workspace";
  @NonNls private static final String ELEMENT_MACRO = "macro";
  private GlobalSearchScope myAllScope;
  private GlobalSearchScope myProjectScope;
  @NonNls private static final String TEMPLATE_PROJECT_NAME = "Default (Template) Project";
  @NonNls private static final String DUMMY_PROJECT_NAME = "Dummy (Mock) Project";

  protected ProjectImpl(ProjectManagerImpl manager,
                        String filePath,
                        boolean isDefault,
                        boolean isOptimiseTestLoadSpeed,
                        PathMacrosImpl pathMacros) {
    super(isDefault, pathMacros);

    myOptimiseTestLoadSpeed = isOptimiseTestLoadSpeed;

    Extensions.instantiateArea(PluginManager.AREA_IDEA_PROJECT, this, null);

    getPicoContainer().registerComponentInstance(Project.class, this);

    myManager = manager;
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

  public synchronized Element getConfiguration(String name) {
    return super.getConfiguration(name);
  }

  public boolean isDummy() {
    return myDummy;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  public boolean isOpen() {
    return ProjectManagerEx.getInstanceEx().isProjectOpened(this);
  }

  public boolean isInitialized() {
    if (!isOpen() || isDisposed()) return false;
    return StartupManagerEx.getInstanceEx(this).startupActivityPassed();
  }

  public PomModel getModel() {
    return myModel != null ? myModel : (myModel = getComponent(PomModel.class));
  }

  public GlobalSearchScope getAllScope() {
    if (myAllScope == null) {
      final ProjectRootManager projectRootManager = getComponent(ProjectRootManager.class);
      myAllScope = new GlobalSearchScope() {
        final ProjectFileIndex myProjectFileIndex = projectRootManager.getFileIndex();

        public boolean contains(VirtualFile file) {
          return true;
        }

        public int compare(VirtualFile file1, VirtualFile file2) {
          List<OrderEntry> entries1 = myProjectFileIndex.getOrderEntriesForFile(file1);
          List<OrderEntry> entries2 = myProjectFileIndex.getOrderEntriesForFile(file2);
          if (entries1.size() != entries2.size()) return 0;

          int res = 0;
          for (OrderEntry entry1 : entries1) {
            Module module = entry1.getOwnerModule();
            ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
            OrderEntry entry2 = moduleFileIndex.getOrderEntryForFile(file2);
            if (entry2 == null) {
              return 0;
            }
            else {
              int aRes = entry2.compareTo(entry1);
              if (aRes == 0) return 0;
              if (res == 0) {
                res = aRes;
              }
              else if (res != aRes) {
                return 0;
              }
            }
          }

          return res;
        }

        public boolean isSearchInModuleContent(Module aModule) {
          return true;
        }

        public boolean isSearchInLibraries() {
          return true;
        }

        public String getDisplayName() {
          return PsiBundle.message("psi.search.scope.project.and.libraries");
        }

        public String toString() {
          return getDisplayName();
        }
      };
    }
    return myAllScope;
  }

  public GlobalSearchScope getProjectScope() {
    if (myProjectScope == null) {
      final ProjectRootManager projectRootManager = getComponent(ProjectRootManager.class);
      myProjectScope = new GlobalSearchScope() {
        private final ProjectFileIndex myFileIndex = projectRootManager.getFileIndex();

        public boolean contains(VirtualFile file) {
          return myFileIndex.isInContent(file);
        }

        public int compare(VirtualFile file1, VirtualFile file2) {
          return 0;
        }

        public boolean isSearchInModuleContent(Module aModule) {
          return true;
        }

        public boolean isSearchInLibraries() {
          return false;
        }

        public String getDisplayName() {
          return PsiBundle.message("psi.search.scope.project");
        }

        public String toString() {
          return getDisplayName();
        }
      };
    }
    return myProjectScope;
  }

  public void setDummy(boolean isDummy) {
    myDummy = isDummy;
  }

  protected ArrayList<String> getConversionProblemsStorage() {
    return myConversionProblemsStorage;
  }

  protected void initJdomExternalizable(Class componentClass, BaseComponent component) {
    if (myOptimiseTestLoadSpeed) return; //test load speed optimization
    doInitJdomExternalizable(componentClass, component);
  }

  public void loadProjectComponents() {
    boolean realProject = !isDummy();
    loadComponentsConfiguration(PROJECT_LAYER, realProject);

    if (realProject && PluginManager.shouldLoadPlugins()) {
      final Application app = ApplicationManager.getApplication();
      final IdeaPluginDescriptor[] plugins = app.getPlugins();
      for (IdeaPluginDescriptor plugin : plugins) {
        if (!PluginManager.shouldLoadPlugin(plugin)) continue;
        final Element projectComponents = plugin.getProjectComponents();
        if (projectComponents != null) {
          loadComponentsConfiguration(projectComponents, plugin, true);
        }
      }
    }
  }

  public ConfigurationFile[] getConfigurationFiles() {
    final List<ConfigurationFile> files = new ArrayList<ConfigurationFile>(2);

    if (myProjectFile != null) {
      files.add(myProjectFile);
    }

    if (myWorkspaceFile != null) {
      files.add(myWorkspaceFile);
    }
    return files.toArray(new ConfigurationFile[files.size()]);
  }

  public String getProjectFilePath() {
    if (myProjectFile == null) return null;
    return myProjectFile.getFilePath();
  }

  public VirtualFile getProjectFile() {
    if (myProjectFile == null) return null;
    return myProjectFile.getVirtualFile();
  }

  public String getName() {
    if (isDefault()) return TEMPLATE_PROJECT_NAME;
    if (isDummy()) return DUMMY_PROJECT_NAME;

    String temp = myProjectFile.getFileName();
    if (temp.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
      temp = temp.substring(0, temp.length() - ProjectFileType.DOT_DEFAULT_EXTENSION.length());
    }
    final int i = temp.lastIndexOf(File.separatorChar);
    if (i >= 0) {
      temp = temp.substring(i + 1, temp.length() - i + 1);
    }
    return temp;
  }

  public VirtualFile getWorkspaceFile() {
    if (myWorkspaceFile == null) return null;
    return myWorkspaceFile.getVirtualFile();
  }

  protected Element getDefaults(BaseComponent component) throws IOException, JDOMException, InvalidDataException {
    //[max | mike] speed optimization. If you need default initialization in tests
    //use field initializers instead.

    if (myOptimiseTestLoadSpeed) return null;

    InputStream stream = DecodeDefaultsUtil.getDefaultsInputStream(component);
    if (stream != null) {
      Document document = JDOMUtil.loadDocument(stream);
      stream.close();

      if (document == null) {
        throw new InvalidDataException();
      }
      Element root = document.getRootElement();
      if (root == null || !ELEMENT_COMPONENT.equals(root.getName())) {
        throw new InvalidDataException();
      }

      getExpandMacroReplacements().substitute(root, SystemInfo.isFileSystemCaseSensitive);
      return root;
    }
    return null;
  }

  protected ComponentManagerImpl getParentComponentManager() {
    return (ComponentManagerImpl)ApplicationManager.getApplication();
  }

  protected String getRootNodeName() {
    return "project";
  }

  protected VirtualFile getComponentConfigurationFile(Class componentInterface) {
    return isWorkspace(componentInterface) ? getWorkspaceFile() : getProjectFile();
  }

  public boolean isOptimiseTestLoadSpeed() {
    return myOptimiseTestLoadSpeed;
  }

  private boolean isWorkspace(Class componentInterface) {
    final Map options = getComponentOptions(componentInterface);
    return options != null && Boolean.parseBoolean((String)options.get(OPTION_WORKSPACE));
  }

  private void getExpandProjectHomeReplacements(ExpandMacroToPathMap result) {
    String projectDir = getProjectDir();
    if (projectDir == null) return;

    File f = new File(projectDir.replace('/', File.separatorChar));

    getExpandProjectHomeReplacements(result, f, "$" + PathMacrosImpl.PROJECT_DIR_MACRO_NAME + "$");
  }

  private static void getExpandProjectHomeReplacements(ExpandMacroToPathMap result, File f, String macro) {
    if (f == null) return;

    getExpandProjectHomeReplacements(result, f.getParentFile(), macro + "/..");
    String path = PathMacroMap.quotePath(f.getAbsolutePath());
    String s = macro;

    if (StringUtil.endsWithChar(path, '/')) s += "/";

    result.put(s, path);
  }

  private void getProjectHomeReplacements(@NonNls ReplacePathToMacroMap result, final boolean savePathsRelative) {
    String projectDir = getProjectDir();
    if (projectDir == null) return;

    File f = new File(projectDir.replace('/', File.separatorChar));
    //LOG.assertTrue(f.exists());

    String macro = "$" + PathMacrosImpl.PROJECT_DIR_MACRO_NAME + "$";

    while (f != null) {
      String path = PathMacroMap.quotePath(f.getAbsolutePath());
      String s = macro;

      if (StringUtil.endsWithChar(path, '/')) s += "/";
      if (path.equals("/")) break;

      result.put("file://" + path, "file://" + s);
      result.put("file:/" + path, "file:/" + s);
      result.put("file:" + path, "file:" + s);
      result.put("jar://" + path, "jar://" + s);
      result.put("jar:/" + path, "jar:/" + s);
      result.put("jar:" + path, "jar:" + s);
      //noinspection HardCodedStringLiteral
      if (!path.equalsIgnoreCase("e:/") && !path.equalsIgnoreCase("r:/")) {
        result.put(path, s);
      }

      if (!savePathsRelative) break;
      macro += "/..";
      f = f.getParentFile();
    }
  }

  private String getProjectDir() {
    if (myProjectFile == null) return null;
    String path = myProjectFile.getFilePath();
    String projectDir = new File(path).getParent();
    if (projectDir == null) return null;
    projectDir = projectDir.replace(File.separatorChar, '/');
    if (projectDir.endsWith(":/")) {
      projectDir = projectDir.substring(0, projectDir.length() - 1);
    }
    return projectDir;
  }

  public ExpandMacroToPathMap getExpandMacroReplacements() {
    ExpandMacroToPathMap result = super.getExpandMacroReplacements();
    getExpandProjectHomeReplacements(result);
    return result;
  }

  public ReplacePathToMacroMap getMacroReplacements() {
    ReplacePathToMacroMap result = new ReplacePathToMacroMap();
    getProjectHomeReplacements(result, false);
    result.putAll(super.getMacroReplacements());
    getProjectHomeReplacements(result, mySavePathsRelative);
    return result;
  }

  public void init() {
    super.init();

    ((ModuleManagerImpl)ModuleManager.getInstance(this)).loadModules();
    myProjectManagerListener = new ProjectImpl.MyProjectManagerListener();
    myManager.addProjectManagerListener(this, myProjectManagerListener);
  }

  /** @noinspection AssignmentToStaticFieldFromInstanceMethod*/
  public void save() {
    if (ApplicationManagerEx.getApplicationEx().isDoNotSave()) return; //no need to save
    ShutDownTracker.getInstance().registerStopperThread(Thread.currentThread());

    try {
      if (!ourSaveSettingsInProgress) {
        try {
          ourSaveSettingsInProgress = true;
          startLoggingUsedMacros();

          saveSettingsSavingComponents();

          final Module[] modules = ModuleManager.getInstance(this).getModules();
          final ReadonlyStatusHandler.OperationStatus operationStatus = ensureConfigFilesWritable(modules);
          if (operationStatus.hasReadonlyFiles()) {
            MessagesEx.error(this, ProjectBundle.message("project.save.error", operationStatus.getReadonlyFilesMessage())).showLater();
            return;
          }

          final Exception[] exception = new Exception[]{null};
          CommandProcessor.getInstance().executeCommand(this, new Runnable() {
            public void run() {
              for (Module module1 : modules) {
                ModuleImpl module = (ModuleImpl)module1;
                try {
                  module._save();
                }
                catch (Exception e) {
                  exception[0] = e;
                  return;
                }
              }
              try {
                // important! must save project after modules are saved, otherwise not all used macros will be saved to the ipr file
                _save();
              }
              catch (Exception e) {
                exception[0] = e;
              }
            }
          }, ProjectBundle.message("project.save.settings.command"), null);

          if (exception[0] != null) {
            LOG.info(exception[0]);
            MessagesEx.error(this, ProjectBundle.message("project.save.error", exception[0].getMessage())).showLater();
          }
        }
        finally {
          endLoggingUsedMacros();
          ourSaveSettingsInProgress = false;
        }
      }
    }
    finally {
      ShutDownTracker.getInstance().unregisterStopperThread(Thread.currentThread());
    }
  }

  private ReadonlyStatusHandler.OperationStatus ensureConfigFilesWritable(Module[] modules) {
    final List<VirtualFile> readonlyFiles = new ArrayList<VirtualFile>(modules.length + 2);

    collectReadonlyFiles(getConfigurationFiles(), readonlyFiles);

    for (Module module1 : modules) {
      final ModuleImpl module = (ModuleImpl)module1;
      module.collectReadonlyFiles(module.getConfigurationFiles(), readonlyFiles);
    }

    return ReadonlyStatusHandler.getInstance(this).ensureFilesWritable(readonlyFiles.toArray(new VirtualFile[readonlyFiles.size()]));
  }

  public Element saveToXml(Element targetRoot, VirtualFile configFile) {
    final Element root = super.saveToXml(targetRoot, configFile);
    if (!isMacroLoggingEnabled()) {
      return root;
    }
    VirtualFile projectFile = getProjectFile();
    if (projectFile != null && projectFile.equals(configFile)) {
      final String[] usedMacros = getUsedMacroNames();
      // need this in order to keep file looking the same for vcs if macro set is not changed
      Arrays.sort(usedMacros, ourStringComparator);
      final Element allMacrosElement = new Element(USED_MACROS_ELEMENT_NAME);
      root.addContent(allMacrosElement);
      for (final String usedMacro : usedMacros) {
        final Element macroElem = new Element(ELEMENT_MACRO);
        allMacrosElement.addContent(macroElem);
        macroElem.setAttribute(ATTRIBUTE_NAME, usedMacro);
      }
    }
    return root;
  }

  public static String[] readUsedMacros(Element root) {
    Element child = root.getChild(USED_MACROS_ELEMENT_NAME);
    if (child == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    final List children = child.getChildren(ELEMENT_MACRO);
    final List<String> macroNames = new ArrayList<String>(children.size());
    for (final Object aChildren : children) {
      final Element macro = (Element)aChildren;
      String macroName = macro.getAttributeValue(ATTRIBUTE_NAME);
      if (macroName != null) {
        macroNames.add(macroName);
      }
    }
    return macroNames.toArray(new String[macroNames.size()]);
  }

  public void dispose() {
    LOG.assertTrue(!myDisposed);
    if (myProjectManagerListener != null) {
      myManager.removeProjectManagerListener(this, myProjectManagerListener);
    }

    disposeComponents();
    Extensions.disposeArea(this);
    myManager = null;
    myModel = null;
    myProjectFile = null;
    myWorkspaceFile = null;
    myProjectManagerListener = null;
    super.dispose();
    myDisposed = true;
  }

  private void projectOpened() {
    final Object[] components = getComponents(false);
    for (Object component : components) {
      if (component instanceof ProjectComponent) {
        try {
          ProjectComponent projectComponent = (ProjectComponent)component;
          projectComponent.projectOpened();
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  private void projectClosed() {
    final Object[] components = getComponents(false);
    for (int i = components.length - 1; i >= 0; i--) {
      if (components[i] instanceof ProjectComponent) {
        try {
          ProjectComponent component = (ProjectComponent)components[i];
          component.projectClosed();
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  private class MyProjectManagerListener implements ProjectManagerListener {
    public void projectOpened(Project project) {
      LOG.assertTrue(project == ProjectImpl.this);
      ProjectImpl.this.projectOpened();
    }

    public void projectClosed(Project project) {
      LOG.assertTrue(project == ProjectImpl.this);
      ProjectImpl.this.projectClosed();
    }

    public boolean canCloseProject(Project project) {
      return true;
    }

    public void projectClosing(Project project) {
    }
  }
}
