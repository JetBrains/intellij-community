package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
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

public class ModuleStoreImpl extends BaseFileConfigurableStoreImpl implements IModuleStore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.ModuleStoreImpl");
  @NonNls private static final String RELATIVE_PATHS_OPTION = "relativePaths";

  private ModuleImpl myModule;

  @Nullable
  private ConfigurationFile myFile;


  public ModuleStoreImpl(final ComponentManagerImpl componentManager, final ModuleImpl module) {
    super(componentManager);
    myModule = module;
  }

  @Nullable
  protected Element getDefaults(final Object component) throws JDOMException, IOException, InvalidDataException {
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

  public synchronized Element saveToXml(final VirtualFile configFile) {
    final Element root = super.saveToXml(configFile);
    Set<String> options = myModule.myOptions.keySet();
    for (String option : options) {
      root.setAttribute(option, myModule.myOptions.get(option));
    }
    return root;
  }

  public synchronized void loadFromXml(final Element root, final String filePath) throws InvalidDataException {

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
    return myFile == null ? "" : myFile.getFilePath();
  }

  @NotNull
  public String getModuleFileName() {
    return myFile == null ? "" : myFile.getFileName();
  }


  @Override
  protected void initJdomExternalizable(final Class componentClass, final Object component) {
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

  // since this option is stored in 2 different places (a field in the base class and myOptions map in this class),
  // we have to update both storages whenever the value of the option changes
  public synchronized void setSavePathsRelative(boolean value) {
    super.setSavePathsRelative(value);
    myModule.setOption(RELATIVE_PATHS_OPTION, String.valueOf(value));
  }

  synchronized String getLineSeparator(final VirtualFile file) {
    return FileDocumentManager.getInstance().getLineSeparator(file, myModule.getProject());
  }

  public synchronized void initStore() {
    getComponentManager().initComponentsFromExtensions(Extensions.getArea(myModule));
  }
}
