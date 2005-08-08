package com.intellij.openapi.module.impl;

import com.intellij.application.options.ExpandMacroToPathMap;
import com.intellij.application.options.PathMacroMap;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.ide.plugins.PluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.BaseFileConfigurable;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModule;
import com.intellij.pom.core.impl.PomModuleImpl;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author max
 */
public class ModuleImpl extends BaseFileConfigurable implements Module {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.module.impl.ModuleImpl");

  @NotNull private final Project myProject;
  private VirtualFilePointer myFilePointer;
  private ModuleType myModuleType = null;
  private boolean myIsDisposed = false;
  private MyVirtualFileListener myVirtualFileListener;
  private boolean isModuleAdded;
  private Map<String, String> myOptions = new HashMap<String, String>();

  private static final String MODULE_LAYER = "module-components";
  @NotNull private PomModule myPomModule;
  @NotNull private PomModel myPomModel;

  public ModuleImpl(String filePath, Project project, PomModel pomModel, PathMacrosImpl pathMacros) {
    super(false, pathMacros);
    myProject = project;
    myPomModel =  pomModel;
    Extensions.instantiateArea("IDEA_MODULE", this, project);
    init(filePath);
  }

  private void init(String filePath) {
    getPicoContainer().registerComponentInstance(Module.class, this);
    myPomModule = new PomModuleImpl(myPomModel, this);

    LOG.assertTrue(filePath != null);
    final String path = filePath.replace(File.separatorChar, '/');
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
      }
    });

    String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, path);
    myFilePointer = VirtualFilePointerManager.getInstance().create(url, null);
    myVirtualFileListener = new MyVirtualFileListener();
    VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener);
  }

  protected void initJdomExternalizable(Class componentClass, BaseComponent component) {
    if (((ProjectImpl)myProject).isOptimiseTestLoadSpeed()) return; //test load speed optimization
    doInitJdomExternalizable(componentClass, component);
  }

  public void loadModuleComponents() {
    loadComponentsConfiguration(MODULE_LAYER);

    if (PluginManager.shouldLoadPlugins()) {
      final PluginDescriptor[] plugins = PluginManager.getPlugins();
      for (PluginDescriptor plugin : plugins) {
        if (!PluginManager.shouldLoadPlugin(plugin)) continue;

        final Element moduleComponents = plugin.getModuleComponents();
        if (moduleComponents != null) {
          loadComponentsConfiguration(moduleComponents, plugin);
        }
      }
    }
  }

  protected boolean isComponentSuitable(Map<String,String> options) {
    if (!super.isComponentSuitable(options)) return false;
    if (options == null) return true;

    Set<String> optionNames = options.keySet();
    for (String optionName : optionNames) {
      if (Comparing.equal("workspace", optionName)) continue;
      if (!parseOptionValue(options.get(optionName)).contains(getOptionValue(optionName))) return false;
    }

    return true;
  }

  // since this option is stored in 2 different places (a field in the base class and myOptions map in this class),
  // we have to update both storages whenever the value of the option changes
  public void setSavePathsRelative(boolean value) {
    super.setSavePathsRelative(value);
    setOption(RELATIVE_PATHS_OPTION, String.valueOf(value));
  }

  private static List<String> parseOptionValue(String optionValue) {
    if (optionValue == null) return new ArrayList<String>(0);
    return Arrays.asList(optionValue.split(";"));
  }

  protected Element getDefaults(BaseComponent component) throws IOException, JDOMException, InvalidDataException {
    //[mike] speed optimization. If you need default initialization in tests
    //use field initializers instead.
    if (((ProjectImpl)myProject).isOptimiseTestLoadSpeed()) return null;

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

      //TODO:
      //JDOMUtil.replaceText(root, getExpandMacroReplacements(), SystemInfo.isFileSystemCaseSensitive);

      return root;
    }
    return null;
  }

  protected ComponentManagerImpl getParentComponentManager() {
    return (ComponentManagerImpl)myProject;
  }

  public VirtualFile getModuleFile() {
    return myFilePointer.getFile();
  }

  void rename(String newName) {
    final VirtualFile file = myFilePointer.getFile();
    try {
      if (file != null) {
        file.rename(ModuleUtil.MODULE_RENAMING_REQUESTOR, newName + ".iml");
        return;
      }
    }
    catch (IOException e) {
    }
    // [dsl] we get here if either old file didn't exist or renaming failed
    final File oldFile = new File(getModuleFilePath());
    final File parentFile = oldFile.getParentFile();
    final File newFile = new File(parentFile, newName + ".iml");
    final String newUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, newFile.getPath().replace(File.separatorChar, '/'));
    myFilePointer = VirtualFilePointerManager.getInstance().create(newUrl, null);
  }

  @NotNull
  public String getModuleFilePath() {
    String url = myFilePointer.getUrl();
    String protocol = VirtualFileManager.extractProtocol(url);
    LOG.assertTrue(LocalFileSystem.PROTOCOL.equals(protocol));
    String path = VirtualFileManager.extractPath(url);
    return path.replace('/', File.separatorChar);
  }

  public void dispose() {
    isModuleAdded = false;
    disposeComponents();
    myIsDisposed = true;
    VirtualFileManager.getInstance().removeVirtualFileListener(myVirtualFileListener);
    Extensions.disposeArea(this);
  }

  public VirtualFile[] getConfigurationFiles() {
    final VirtualFile moduleFile = getModuleFile();
    return moduleFile == null? VirtualFile.EMPTY_ARRAY : new VirtualFile[]{moduleFile};
  }

  public String[] getConfigurationFilePaths() {
    return new String[]{getModuleFilePath()};
  }

  protected String getRootNodeName() {
    return "module";
  }

  protected VirtualFile getComponentConfigurationFile(Class componentInterface) {
    return getModuleFile();
  }

  public ReplacePathToMacroMap getMacroReplacements() {
    ReplacePathToMacroMap result = new ReplacePathToMacroMap();
    getModuleHomeReplacements(result, false);
    result.putAll(super.getMacroReplacements());
    getModuleHomeReplacements(result, mySavePathsRelative);
    return result;
  }

  public ExpandMacroToPathMap getExpandMacroReplacements() {
    ExpandMacroToPathMap result = new ExpandMacroToPathMap();
    getExpandModuleHomeReplacements(result);
    result.putAll(super.getExpandMacroReplacements());
    return result;
  }

  private void getExpandModuleHomeReplacements(ExpandMacroToPathMap result) {
    String moduleDir = getModuleDir(getModuleFilePath());
    if (moduleDir == null) return;

    File f = new File(moduleDir.replace('/', File.separatorChar));
    LOG.assertTrue(f.exists());

    getExpandModuleHomeReplacements(result, f, "$" + PathMacrosImpl.MODULE_DIR_MACRO_NAME + "$");
  }

  private void getExpandModuleHomeReplacements(ExpandMacroToPathMap result, File f, String macro) {
    if (f == null) return;

    getExpandModuleHomeReplacements(result, f.getParentFile(), macro + "/..");
    String path = PathMacroMap.quotePath(f.getAbsolutePath());
    String s = macro;

    if (StringUtil.endsWithChar(path, '/')) s += "/";

    result.put(s, path);
  }

  private void getModuleHomeReplacements(ReplacePathToMacroMap result, final boolean addRelativePathMacros) {
    String moduleDir = getModuleDir(getModuleFilePath());
    if (moduleDir == null) return;

    File f = new File(moduleDir.replace('/', File.separatorChar));
    // [dsl]: Q?
    //if(!f.exists()) return;

    String macro = "$" + PathMacrosImpl.MODULE_DIR_MACRO_NAME + "$";

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

      if (!addRelativePathMacros) break;
      macro += "/..";
      f = f.getParentFile();
    }
  }

  private static String getModuleDir(String moduleFilePath) {
    String moduleDir = new File(moduleFilePath).getParent();
    if (moduleDir == null) return null;
    moduleDir = moduleDir.replace(File.separatorChar, '/');
    if (moduleDir.endsWith(":/")) {
      moduleDir = moduleDir.substring(0, moduleDir.length() - 1);
    }
    return moduleDir;
  }

  public void loadFromXml(Element root, String filePath) throws InvalidDataException {
    List attributes = root.getAttributes();
    for (int i = 0; i < attributes.size(); i++) {
      Attribute attr = (Attribute)attributes.get(i);
      setOption(attr.getName(), attr.getValue());
    }

    final String moduleTypeId = getOptionValue("type");
    final ModuleType moduleType = moduleTypeId == null ? ModuleType.JAVA : ModuleTypeManager.getInstance().findByID(moduleTypeId);
    setModuleType(moduleType);
    super.loadFromXml(root, filePath);
  }

  public Element saveToXml(Element targetRoot, VirtualFile configFile) {
    final Element root = super.saveToXml(targetRoot, configFile);
    Set<String> options = myOptions.keySet();
    for (String option : options) {
      root.setAttribute(option, myOptions.get(option));
    }
    return root;
  }

  public void projectOpened() {
    final BaseComponent[] components = getComponents(false);
    for (BaseComponent component1 : components) {
      ModuleComponent component = (ModuleComponent)component1;
      try {
        component.projectOpened();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  public void projectClosed() {
    final BaseComponent[] components = getComponents(false);
    for (BaseComponent component1 : components) {
      ModuleComponent component = (ModuleComponent)component1;
      try {
        component.projectClosed();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  @NotNull
  public ModuleType getModuleType() {
    LOG.assertTrue(myModuleType != null, "Module type not initialized yet");
    return myModuleType;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  private final Map<String, String> myFileToModuleName = new HashMap<String, String>();

  @NotNull
  public String getName() {
    final String fileName = myFilePointer.getFileName();
    String moduleName = myFileToModuleName.get(fileName);
    if (moduleName == null) {
      moduleName = ModuleUtil.moduleNameByFileName(fileName);
      myFileToModuleName.put(fileName, moduleName);
    }
    return moduleName;
  }

  public boolean isDisposed() {
    return myIsDisposed;
  }

  public void moduleAdded() {
    isModuleAdded = true;
    final BaseComponent[] components = getComponents(false);
    for (BaseComponent component1 : components) {
      ModuleComponent component = (ModuleComponent)component1;
      component.moduleAdded();
    }
  }

  public void setModuleType(ModuleType type) {
    myModuleType = type;
    setOption("type", type.getId());
  }

  protected String getConfigurableType() {
    return "module";
  }

  public void setOption(String optionName, String optionValue) {
    myOptions.put(optionName, optionValue);
  }

  public String getOptionValue(String optionName) {
    return myOptions.get(optionName);
  }

  @NotNull
  public PomModule getPom() {
    return myPomModule;
  }

  public String toString() {
    return "Module:" + getName();
  }

  private class MyVirtualFileListener extends VirtualFileAdapter {
    public void propertyChanged(VirtualFilePropertyEvent event) {
      if (!isModuleAdded) return;
      final Object requestor = event.getRequestor();
      if (ModuleUtil.MODULE_RENAMING_REQUESTOR.equals(requestor)) return;
      if (!VirtualFile.PROP_NAME.equals(event.getPropertyName())) return;
      final VirtualFile moduleFile = getModuleFile();
      if (moduleFile == null) return;
      if (moduleFile.equals(event.getFile())) {
        ModuleManagerImpl.getInstanceImpl(getProject()).fireModuleRenamedByVfsEvent(ModuleImpl.this);
      }
    }

  }
}
