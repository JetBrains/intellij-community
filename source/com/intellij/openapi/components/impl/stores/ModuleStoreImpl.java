package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;

import java.io.File;
import java.io.IOException;
import java.util.Set;

class ModuleStoreImpl extends BaseFileConfigurableStoreImpl implements IModuleStore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.ModuleStoreImpl");
  @NonNls private static final String RELATIVE_PATHS_OPTION = "relativePaths";
  @NonNls private static final String MODULE_FILE_MACRO = "MODULE_FILE";

  private ModuleImpl myModule;

  private static final String DEFAULT_STATE_STORAGE = "$" + MODULE_FILE_MACRO + "$";


  @SuppressWarnings({"UnusedDeclaration"})
  public ModuleStoreImpl(final ComponentManagerImpl componentManager, final ModuleImpl module) {
    super(componentManager);
    myModule = module;
  }

  @Override
  protected void writeRootElement(final org.w3c.dom.Element rootElement) {
    super.writeRootElement(rootElement);

    Set<String> options = myModule.myOptions.keySet();
    for (String option : options) {
      rootElement.setAttribute(option, myModule.myOptions.get(option));
    }
  }

  protected XmlElementStorage getMainStorage() {
    return (XmlElementStorage)getStateStorageManager().getStateStorage(DEFAULT_STATE_STORAGE);
  }

  @Override
  public void load() throws IOException {
    super.load();

    try {
      final StateStorage stateStorage = getStateStorageManager().getStateStorage(DEFAULT_STATE_STORAGE);
      assert stateStorage instanceof FileBasedStorage;
      FileBasedStorage fileBasedStorage = (FileBasedStorage)stateStorage;

      Document doc = fileBasedStorage.getDocument();
      final org.w3c.dom.Element element = doc.getDocumentElement();

      final NamedNodeMap attributes = element.getAttributes();
      for (int i = 0; i < attributes.getLength(); i++) {
        Attr attr = (Attr)attributes.item(i);
        myModule.setOption(attr.getName(), attr.getValue());
      }

      final String moduleTypeId = myModule.getOptionValue(ModuleImpl.ELEMENT_TYPE);
      final ModuleType moduleType = moduleTypeId == null ? ModuleType.JAVA : ModuleTypeManager.getInstance().findByID(moduleTypeId);
      myModule.setModuleType(moduleType);
    }
    catch (StateStorage.StateStorageException e) {
      LOG.info(e);
      throw new IOException(e.getMessage());
    }
  }

  public void setModuleFilePath(final String filePath) {
    LOG.assertTrue(filePath != null);
    final String path = filePath.replace(File.separatorChar, '/');
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
      }
    });

    getStateStorageManager().addMacro(MODULE_FILE_MACRO, path);
  }

  @Nullable
  public VirtualFile getModuleFile() {
    try {
      return ((FileBasedStorage)getStateStorageManager().getStateStorage(DEFAULT_STATE_STORAGE)).getVirtualFile();
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  @NotNull
  public String getModuleFilePath() {
    return ((FileBasedStorage)getStateStorageManager().getStateStorage(DEFAULT_STATE_STORAGE)).getFilePath();
  }

  @NotNull
  public String getModuleFileName() {
    return ((FileBasedStorage)getStateStorageManager().getStateStorage(DEFAULT_STATE_STORAGE)).getFileName();
  }

  @Override
  public void initComponent(final Object component) {
    if (((ProjectImpl)myModule.getProject()).isOptimiseTestLoadSpeed()) return; //test load speed optimization
    super.initComponent(component);
  }

  public boolean isSavePathsRelative() {
    return super.isSavePathsRelative();
  }

  @Nullable
  protected VirtualFile getComponentConfigurationFile(ComponentConfig componentInterface) {
    return getModuleFile();
  }

  @NonNls
  protected String getRootNodeName() {
    return "module";
  }

  // since this option is stored in 2 different places (a field in the base class and myOptions map in this class),
  // we have to update both storages whenever the value of the option changes
  public void setSavePathsRelative(boolean value) {
    super.setSavePathsRelative(value);
    myModule.setOption(RELATIVE_PATHS_OPTION, String.valueOf(value));
  }

  synchronized String getLineSeparator(final VirtualFile file) {
    return FileDocumentManager.getInstance().getLineSeparator(file, myModule.getProject());
  }

  public synchronized void initStore() {
  }


  @Override
  protected StateStorage getOldStorage(final Object component) {
    return getStateStorageManager().getStateStorage(DEFAULT_STATE_STORAGE);
  }

  protected StateStorageManager createStateStorageManager() {
    return new ModuleStateStorageManager(PathMacroManager.getInstance(getComponentManager()).createTrackingSubstitutor(), myModule);
  }
}
