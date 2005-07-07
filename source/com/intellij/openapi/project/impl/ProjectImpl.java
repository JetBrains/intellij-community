package com.intellij.openapi.project.impl;

import com.intellij.application.options.ExpandMacroToPathMap;
import com.intellij.application.options.PathMacroMap;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.ide.plugins.PluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.pom.PomModel;
import com.intellij.util.ArrayUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;


/**
 *
 */
public class ProjectImpl extends BaseFileConfigurable implements ProjectEx, AreaInstance {
  private static final Logger LOG = Logger.getInstance("#com.intellij.project.impl.ProjectImpl");

  private ProjectManagerImpl myManager;
  private VirtualFilePointer myFilePointer;

  private MyProjectManagerListener myProjectManagerListener;
  private boolean myDummy;

  private ArrayList<String> myConversionProblemsStorage = new ArrayList<String>();

  private static boolean ourSaveSettingsInProgress = false;

  private static final String WORKSPACE_EXTENSION = ".iws";
  private static final String PROJECT_LAYER = "project-components";
  private boolean myDisposed = false;
  private PomModel myModel = null;

  private final boolean myOptimiseTestLoadSpeed;
  private static final String USED_MACROS_ELEMENT_NAME = "UsedPathMacros";
  private static final Comparator<String> ourStringComparator = new Comparator<String>() {
    public int compare(String o1, String o2) {
      return o1.compareTo(o2);
    }
  };

  protected ProjectImpl(ProjectManagerImpl manager,
                        String filePath,
                        boolean isDefault,
                        boolean isOptimiseTestLoadSpeed,
                        PathMacrosImpl pathMacros, VirtualFilePointerManager filePointerManager) {
    super(isDefault, pathMacros);

    myOptimiseTestLoadSpeed = isOptimiseTestLoadSpeed;

    Extensions.instantiateArea("IDEA_PROJECT", this, null);

    getPicoContainer().registerComponentInstance(Project.class, this);

    myManager = manager;
    if (filePath != null) {
      String path = filePath.replace(File.separatorChar, '/');
      String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, path);
      myFilePointer = filePointerManager.create(url, null);
    }
    else {
      myFilePointer = null;
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
    loadComponentsConfiguration(PROJECT_LAYER);

    if (PluginManager.shouldLoadPlugins()) {
      final PluginDescriptor[] plugins = PluginManager.getPlugins();
      for (int i = 0; i < plugins.length; i++) {
        PluginDescriptor plugin = plugins[i];
        if (!PluginManager.shouldLoadPlugin(plugin)) continue;
        final Element projectComponents = plugin.getProjectComponents();
        if (projectComponents != null) {
          loadComponentsConfiguration(projectComponents, plugin);
        }
      }
    }
  }

  public VirtualFile[] getConfigurationFiles() {
    final List<VirtualFile> files = new ArrayList<VirtualFile>(2);

    final VirtualFile projectFile = getProjectFile();
    if (projectFile != null) {
      files.add(projectFile);
    }

    final VirtualFile workspaceFile = getWorkspaceFile();
    if (workspaceFile != null) {
      files.add(workspaceFile);
    }
    return files.toArray(new VirtualFile[files.size()]);
  }

  public String[] getConfigurationFilePaths() {
    if (myFilePointer == null) return null;
    String iprPath = getProjectFilePath();

    int dotIdx = iprPath.lastIndexOf('.');
    if (dotIdx < 0) dotIdx = iprPath.length();
    String iwsPath = iprPath.substring(0, dotIdx) + WORKSPACE_EXTENSION;

    return new String[]{iprPath, iwsPath};
  }

  public String getProjectFilePath() {
    if (myFilePointer == null) return null;
    String iprUrl = myFilePointer.getUrl();
    String iprProtocol = VirtualFileManager.extractProtocol(iprUrl);
    LOG.assertTrue(LocalFileSystem.PROTOCOL.equals(iprProtocol));
    String iprPath = VirtualFileManager.extractPath(iprUrl).replace('/', File.separatorChar);
    return iprPath;
  }

  public VirtualFile getProjectFile() {
    if (myFilePointer == null) return null;
    VirtualFile file = myFilePointer.getFile();
    /* commented out to fix # 25591
    if (file == null){
      //???
      return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
        public VirtualFile compute() {
          return VirtualFileManager.getInstance().refreshAndFindFileByUrl(myFilePointer.getUrl());
        }
      });
    }
    */
    return file;
  }

  public String getName() {
    final VirtualFile projectFile = getProjectFile();
    if (projectFile != null) return projectFile.getNameWithoutExtension();
    String temp = getProjectFilePath();
    if (temp.endsWith(".ipr")) {
      temp = temp.substring(temp.length() - 4);
    }
    final int i = temp.lastIndexOf(File.separatorChar);
    if (i >= 0) {
      temp = temp.substring(i + 1, temp.length() - i + 1);
    }
    return temp;
  }

  public VirtualFile getWorkspaceFile() {
    final VirtualFile projectFile = getProjectFile();
    if (projectFile == null) return null;

    final VirtualFile parent = projectFile.getParent();
    if (parent == null) return null;
    final String name = projectFile.getNameWithoutExtension() + WORKSPACE_EXTENSION;

    VirtualFile workspaceFile = parent.findChild(name);
    return workspaceFile;
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
      if (root == null || !"component".equals(root.getName())) {
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
    if (options == null) return false;
    if ("true".equals(options.get("workspace"))) return true;
    return false;
  }

  private void getExpandProjectHomeReplacements(ExpandMacroToPathMap result) {
    String projectDir = getProjectDir();
    if (projectDir == null) return;

    File f = new File(projectDir.replace('/', File.separatorChar));

    getExpandProjectHomeReplacements(result, f, "$" + PathMacrosImpl.PROJECT_DIR_MACRO_NAME + "$");
  }

  private void getExpandProjectHomeReplacements(ExpandMacroToPathMap result, File f, String macro) {
    if (f == null) return;

    getExpandProjectHomeReplacements(result, f.getParentFile(), macro + "/..");
    String path = PathMacroMap.quotePath(f.getAbsolutePath());
    String s = macro;

    if (StringUtil.endsWithChar(path, '/')) s += "/";

    result.put(s, path);
  }

  private void getProjectHomeReplacements(ReplacePathToMacroMap result, final boolean savePathsRelative) {
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
      if (!path.equalsIgnoreCase("e:/") && !path.equalsIgnoreCase("r:/")) {
        result.put(path, s);
      }

      if (!savePathsRelative) break;
      macro += "/..";
      f = f.getParentFile();
    }
  }

  private String getProjectDir() {
    if (myFilePointer == null) return null;
    String url = myFilePointer.getUrl();
    String path = VirtualFileManager.extractPath(url).replace('/', File.separatorChar);
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

  public void save() {
    ShutDownTracker.getInstance().registerStopperThread(Thread.currentThread());

    try {
      if (!ourSaveSettingsInProgress) {
        try {
          ourSaveSettingsInProgress = true;
          startLoggingUsedMacros();

          saveSettingsSavingComponents();

          final Module[] modules = ModuleManager.getInstance(ProjectImpl.this).getModules();
          final ReadonlyStatusHandler.OperationStatus operationStatus = ensureConfigFilesWritable(modules);
          if (operationStatus.hasReadonlyFiles()) {
            MessagesEx.error(this, "Could not save project:\n" + operationStatus.getReadonlyFilesMessage()).showLater();
            return;
          }

          final Exception[] exception = new Exception[]{null};
          CommandProcessor.getInstance().executeCommand(this, new Runnable() {
            public void run() {
              for (int i = 0; i < modules.length; i++) {
                ModuleImpl module = (ModuleImpl)modules[i];
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
                return;
              }
            }
          }, "Save settings", null);

          if (exception[0] != null) {
            LOG.info(exception[0]);
            MessagesEx.error(this, "Could not save project:\n" + exception[0].getMessage()).showLater();
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

    for (int idx = 0; idx < modules.length; idx++) {
      final ModuleImpl module = (ModuleImpl)modules[idx];
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
      for (int idx = 0; idx < usedMacros.length; idx++) {
        final String usedMacro = usedMacros[idx];
        final Element macroElem = new Element("macro");
        allMacrosElement.addContent(macroElem);
        macroElem.setAttribute("name", usedMacro);
      }
    }
    return root;
  }

  public static String[] readUsedMacros(Element root) {
    Element child = root.getChild(USED_MACROS_ELEMENT_NAME);
    if (child == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    final List children = child.getChildren("macro");
    final List<String> macroNames = new ArrayList<String>(children.size());
    for (Iterator it = children.iterator(); it.hasNext();) {
      final Element macro = (Element)it.next();
      String macroName = macro.getAttributeValue("name");
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
    myDisposed = true;
  }

  private void projectOpened() {
    final BaseComponent[] components = getComponents(false);
    for (int i = 0; i < components.length; i++) {
      try {
        ProjectComponent component = (ProjectComponent)components[i];
        component.projectOpened();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  private void projectClosed() {
    final BaseComponent[] components = getComponents(false);
    for (int i = components.length - 1; i >= 0; i--) {
      try {
        ProjectComponent component = (ProjectComponent)components[i];
        component.projectClosed();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  protected String getConfigurableType() {
    return "project";
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

  public static class ReadOnlyProjectException extends Exception {
    private String myConfigurableType;
    private File myFile;

    public ReadOnlyProjectException(String configurableType, File file) {
      myFile = file;
      myConfigurableType = configurableType;
    }

    public MessagesEx.MessageInfo getMessageInfo(Project project) {
      return MessagesEx.fileIsReadOnly(project, myFile.getAbsolutePath()).setTitle("Cannot save " + myConfigurableType);
    }

    public File getFile() {
      return myFile;
    }
  }
}
