package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

class ApplicationStoreImpl extends ComponentStoreImpl implements IApplicationStore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.ApplicationStoreImpl");

  @NonNls private static final String XML_EXTENSION = ".xml";

  private ApplicationImpl myApplication;
  private StateStorageManager myStateStorageManager;
  @NonNls private static final String APP_CONFIG_STORAGE_MACRO = "APP_CONFIG";
  private DefaultsStateStorage myDefaultsStateStorage;


  @SuppressWarnings({"UnusedDeclaration"}) //picocontainer
  public ApplicationStoreImpl(final ApplicationImpl application) {
    myApplication = application;
    myStateStorageManager = new StateStorageManager(null);
    myDefaultsStateStorage = new DefaultsStateStorage(null);
  }

  public void initStore() {
  }


  @Override
  protected void doSave() throws IOException {
    super.doSave();

    try {
      myStateStorageManager.save();
    }
    catch (StateStorage.StateStorageException e) {
      LOG.info(e);
      throw new IOException(e.getMessage());
    }
  }

  public void load() throws IOException {
    myApplication.initComponents();
  }

  public List<VirtualFile> getAllStorageFiles(final boolean includingSubStructures) {
     return myStateStorageManager.getAllStorageFiles();
   }

  public List<VirtualFile> getAllStorageFilesToSave(final boolean includingSubStructures) {
    try {
      return myStateStorageManager.getAllStorageFilesToSave();
    }
    catch (StateStorage.StateStorageException e) {
      LOG.error(e);
      return Collections.emptyList();
    }
  }


  public void setConfigPath(final String path) {
    myStateStorageManager.addMacro(APP_CONFIG_STORAGE_MACRO, path);
  }

  public static final String DEFAULT_STORAGE_SPEC = "$" + APP_CONFIG_STORAGE_MACRO + "$/" + PathManager.DEFAULT_OPTIONS_FILE_NAME + XML_EXTENSION;

  @Override
  protected StateStorage getStateStorage(@Nullable final Storage storageSpec) {
    assert storageSpec != null;
    return myStateStorageManager.getStateStorage(storageSpec.file());
  }

  @Override
  protected StateStorage getDefaultsStorage() {
    return myDefaultsStateStorage;
  }

  @Override
  protected StateStorage getOldStorage(final Object component) {
    String fileName;

    if (component instanceof NamedJDOMExternalizable) {
      fileName = "$" + APP_CONFIG_STORAGE_MACRO + "$/" + ((NamedJDOMExternalizable)component).getExternalFileName() + XML_EXTENSION;
    }
    else {
      fileName = DEFAULT_STORAGE_SPEC;
    }

    return myStateStorageManager.getStateStorage(fileName);
  }

}
