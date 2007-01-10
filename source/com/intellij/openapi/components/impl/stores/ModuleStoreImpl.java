package com.intellij.openapi.components.impl.stores;

import com.intellij.application.options.ExpandMacroToPathMap;
import com.intellij.application.options.PathMacroMap;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

class ModuleStoreImpl extends BaseFileConfigurableStoreImpl implements IModuleStore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.ModuleStoreImpl");

  private ModuleImpl myModule;
  private ConfigurationFile myFile;

  public void setModule(final ModuleImpl module) {
    myModule = module;
  }

  @Nullable
  protected Element getDefaults(final BaseComponent component) throws JDOMException, IOException, InvalidDataException {
    //[mike] speed optimization. If you need default initialization in tests
    //use field initializers instead.
    if (((ProjectImpl)myModule.getProject()).isOptimiseTestLoadSpeed()) return null;

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

      //TODO:
      //JDOMUtil.replaceText(root, getExpandMacroReplacements(), SystemInfo.isFileSystemCaseSensitive);

      return root;
    }
    return null;
  }

  protected synchronized Element saveToXml(final Element targetRoot, final VirtualFile configFile) {
    final Element root = super.saveToXml(targetRoot, configFile);
    Set<String> options = myModule.myOptions.keySet();
    for (String option : options) {
      root.setAttribute(option, myModule.myOptions.get(option));
    }
    return root;
  }

  protected synchronized void loadFromXml(final Element root, final String filePath) throws InvalidDataException {

    List attributes = root.getAttributes();
    for (Object attribute : attributes) {
      Attribute attr = (Attribute)attribute;
      myModule.setOption(attr.getName(), attr.getValue());
    }

    final String moduleTypeId = myModule.getOptionValue(ModuleImpl.ELEMENT_TYPE);
    final ModuleType moduleType = moduleTypeId == null ? ModuleType.JAVA : ModuleTypeManager.getInstance().findByID(moduleTypeId);
    myModule.setModuleType(moduleType);
    super.loadFromXml(root, filePath);
  }

  public ReplacePathToMacroMap getMacroReplacements() {
    ReplacePathToMacroMap result = new ReplacePathToMacroMap();
    getModuleHomeReplacements(result, false);
    result.putAll(super.getMacroReplacements());
    getModuleHomeReplacements(result, isSavePathsRelative());
    return result;
  }

  public void setModuleFilePath(final String filePath) {
    LOG.assertTrue(filePath != null);
    final String path = filePath.replace(File.separatorChar, '/');
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
      }
    });

    myFile = new ConfigurationFile(path);
  }

  @Nullable
  public VirtualFile getModuleFile() {
    return myFile != null ? myFile.getVirtualFile() : null;
  }

  @NotNull
  public String getModuleFilePath() {
    return myFile.getFilePath();
  }

  @NotNull
  public String getModuleFileName() {
    return myFile.getFileName();
  }

  protected ExpandMacroToPathMap getExpandMacroReplacements() {
    ExpandMacroToPathMap result = new ExpandMacroToPathMap();
    getExpandModuleHomeReplacements(result);
    result.putAll(super.getExpandMacroReplacements());
    return result;
  }

  private void getExpandModuleHomeReplacements(ExpandMacroToPathMap result) {
    String moduleDir = getModuleDir(myModule.getModuleFilePath());
    if (moduleDir == null) return;

    File f = new File(moduleDir.replace('/', File.separatorChar));
    LOG.assertTrue(f.exists());

    getExpandModuleHomeReplacements(result, f, "$" + PathMacrosImpl.MODULE_DIR_MACRO_NAME + "$");
  }

  private static void getExpandModuleHomeReplacements(ExpandMacroToPathMap result, File f, String macro) {
    if (f == null) return;

    getExpandModuleHomeReplacements(result, f.getParentFile(), macro + "/..");
    String path = PathMacroMap.quotePath(f.getAbsolutePath());
    String s = macro;

    if (StringUtil.endsWithChar(path, '/')) s += "/";

    result.put(s, path);
  }

  private void getModuleHomeReplacements(@NonNls ReplacePathToMacroMap result, final boolean addRelativePathMacros) {
    String moduleDir = getModuleDir(myModule.getModuleFilePath());
    if (moduleDir == null) return;

    File f = new File(moduleDir.replace('/', File.separatorChar));
    // [dsl]: Q?
    //if(!f.exists()) return;

    String macro = "$" + PathMacrosImpl.MODULE_DIR_MACRO_NAME + "$";

    while (f != null) {
      @NonNls String path = PathMacroMap.quotePath(f.getAbsolutePath());
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

  @Nullable
  private static String getModuleDir(String moduleFilePath) {
    String moduleDir = new File(moduleFilePath).getParent();
    if (moduleDir == null) return null;
    moduleDir = moduleDir.replace(File.separatorChar, '/');
    if (moduleDir.endsWith(":/")) {
      moduleDir = moduleDir.substring(0, moduleDir.length() - 1);
    }
    return moduleDir;
  }

  protected void initJdomExternalizable(final Class componentClass, final BaseComponent component) {
    if (((ProjectImpl)myModule.getProject()).isOptimiseTestLoadSpeed()) return; //test load speed optimization
    super.initJdomExternalizable(componentClass, component);
  }


  public synchronized void save() throws IOException {
    super.save();
  }


  public synchronized boolean isSavePathsRelative() {
    return super.isSavePathsRelative();
  }

  public ConfigurationFile[] getConfigurationFiles() {
    return new ConfigurationFile[]{myFile};
  }

  @Nullable
  protected VirtualFile getComponentConfigurationFile(Class componentInterface) {
    return getModuleFile();
  }

  @NonNls
  protected String getRootNodeName() {
    return "module";
  }
}
