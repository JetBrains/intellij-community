package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

//todo: extends from base store class
public class DefaultProjectStoreImpl extends ProjectStoreImpl {
  private @Nullable Element myElement;
  private ProjectManagerImpl myProjectManager;

  public DefaultProjectStoreImpl(final ComponentManagerImpl componentManager, final ProjectImpl project, final ProjectManagerImpl projectManager) {
    super(componentManager, project, projectManager);
    myProjectManager = projectManager;

    final Element projectRootElement = projectManager.getDefaultProjectRootElement();

    if (projectRootElement != null) {
      myElement = projectRootElement;
    }
  }


  @Override
  protected StateStorageManager createStateStorageManager() {
    final PathMacroManager pathMacroManager = PathMacroManager.getInstance(getComponentManager());

    final XmlElementStorage storage = new XmlElementStorage(pathMacroManager) {

      @Nullable
      protected Element getRootElement() throws StateStorageException {
        return myElement;
      }

      public void doSave() throws StateStorageException {
        if (myElement != null) {
          myProjectManager.setDefaultProjectRootElement(myElement);
        }
      }

      public boolean  needsSave() throws StateStorageException {
        return true;
      }

      public List<VirtualFile> getAllStorageFiles() {
        return Collections.emptyList();
      }
    };

    return new StateStorageManager(pathMacroManager, "project") {
      @Override
      public synchronized StateStorage getStateStorage(final Storage storageSpec) {
        return storage;
      }

      @Override
      @Nullable
      public StateStorage getFileStateStorage(final String fileName) {
        return storage;
      }

      @Override
      public synchronized void save() throws StateStorage.StateStorageException, IOException {
        super.save();
        storage.save();
      }
    };
  }
}
