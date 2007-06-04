package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

abstract class BaseFileConfigurableStoreImpl extends ComponentStoreImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.BaseFileConfigurableStoreImpl");

  private int myOriginalVersion = -1;
  final HashMap<String,String> myConfigurationNameToFileName = new HashMap<String,String>();
  @NonNls protected static final String RELATIVE_PATHS_OPTION = "relativePaths";
  @NonNls protected static final String VERSION_OPTION = "version";
  @NonNls public static final String ATTRIBUTE_NAME = "name";
  @NonNls static final String ELEMENT_COMPONENT = "component";
  @NonNls private static final String ATTRIBUTE_CLASS = "class";
  private ComponentManager myComponentManager;
  private static ArrayList<String> ourConversionProblemsStorage = new ArrayList<String>();
  private DefaultsStateStorage myDefaultsStateStorage;
  private StateStorageManager myStateStorageManager;


  protected BaseFileConfigurableStoreImpl(final ComponentManager componentManager) {
    myComponentManager = componentManager;
    final PathMacroManager pathMacroManager = PathMacroManager.getInstance(myComponentManager);
    myDefaultsStateStorage = new DefaultsStateStorage(pathMacroManager);
  }

  public synchronized ComponentManager getComponentManager() {
    return myComponentManager;
  }

  protected static class BaseStorageData extends XmlElementStorage.StorageData {
    private boolean mySavePathsRelative;
    protected int myVersion;


    public BaseStorageData(final String rootElementName) {
      super(rootElementName);
    }

    protected BaseStorageData(BaseStorageData storageData) {
      super(storageData);

      mySavePathsRelative = storageData.mySavePathsRelative;
      myVersion = ProjectManagerImpl.CURRENT_FORMAT_VERSION;
    }

    protected void load(@NotNull final Element rootElement) throws IOException {
      super.load(rootElement);

      final String rel = rootElement.getAttributeValue(RELATIVE_PATHS_OPTION);
      if (rel != null) mySavePathsRelative = Boolean.parseBoolean(rel);
    }

    @NotNull
    protected Element save() {
      final Element root = super.save();

      root.setAttribute(RELATIVE_PATHS_OPTION, String.valueOf(mySavePathsRelative));
      root.setAttribute(VERSION_OPTION, Integer.toString(myVersion));

      return root;
    }

    public XmlElementStorage.StorageData clone() {
      return new BaseStorageData(this);
    }

    protected int computeHash() {
      int result = super.computeHash();

      if (mySavePathsRelative) result = result*31 + 1;
      result = result*31 + myVersion;

      return result;
    }
  }

  protected abstract XmlElementStorage getMainStorage();

  public synchronized void loadFromXml(Element root, String filePath) throws InvalidDataException {
    throw new UnsupportedOperationException("");
    /*
    PathMacroManager.getInstance(myComponentManager).expandPaths(root);

    int originalVersion = 0;
    try {
      originalVersion = Integer.parseInt(root.getAttributeValue(VERSION_OPTION));
    }
    catch (NumberFormatException e) {
      LOG.info(e);
    }
    if (originalVersion < 1) {
      Convertor01.execute(root);
    }
    if (originalVersion < 2) {
      Convertor12.execute(root);
    }
    if (originalVersion < 3) {
      Convertor23.execute(root);
    }
    if (originalVersion < 4) {
      Convertor34.execute(root, filePath, getConversionProblemsStorage());
    }

    if (getOriginalVersion() == -1) myOriginalVersion = originalVersion;
    myOriginalVersion = Math.min(getOriginalVersion(), originalVersion);

    String relative = root.getAttributeValue(RELATIVE_PATHS_OPTION);
    if (relative != null) {
      setSavePathsRelative(Boolean.parseBoolean(relative));
    }

    List children = root.getChildren(ELEMENT_COMPONENT);
    for (final Object aChildren : children) {
      Element element = (Element)aChildren;

      String name = element.getAttributeValue(ATTRIBUTE_NAME);
      if (name == null || name.length() == 0) {
        String className = element.getAttributeValue(ATTRIBUTE_CLASS);
        if (className == null) {
          throw new InvalidDataException();
        }
        name = className.substring(className.lastIndexOf('.') + 1);
      }

      addConfiguration(name, element);
      myConfigurationNameToFileName.put(name, filePath);
    }
    */

  }

  @Nullable
  static ArrayList<String> getConversionProblemsStorage() {
    return ourConversionProblemsStorage;
  }

  synchronized int getOriginalVersion() {
    return myOriginalVersion;
  }


  public void load() throws IOException, StateStorage.StateStorageException {
    getMainStorageData(); //load it
  }

  public boolean isSavePathsRelative() {
    try {
      return getMainStorageData().mySavePathsRelative;
    }
    catch (StateStorage.StateStorageException e) {
      LOG.error(e);
      return false;
    }
  }

  public void setSavePathsRelative(boolean b) {
    try {
      getMainStorageData().mySavePathsRelative = b;
    }
    catch (StateStorage.StateStorageException e) {
      LOG.error(e);
    }
  }

  public BaseStorageData getMainStorageData() throws StateStorage.StateStorageException {
    return (BaseStorageData) getMainStorage().getStorageData();
  }

  public List<VirtualFile> getAllStorageFiles(final boolean includingSubStructures) {
    return getStateStorageManager().getAllStorageFiles();
  }


  @Override
  protected StateStorage getDefaultsStorage() {
    return myDefaultsStateStorage;
  }

  public StateStorageManager getStateStorageManager() {
    if (myStateStorageManager == null) {
      myStateStorageManager = createStateStorageManager();
    }
    return myStateStorageManager;
  }

  protected abstract StateStorageManager createStateStorageManager();
}
