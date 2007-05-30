package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StateStorageOperation;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

//todo: extends from base store class
public class DefaultProjectStoreImpl extends ProjectStoreImpl {
  private @Nullable Element myElement;
  private ProjectManagerImpl myProjectManager;
  @NonNls private static final String PROJECT = "project";

  public DefaultProjectStoreImpl(final ProjectImpl project, final ProjectManagerImpl projectManager) {
    super(project);
    myProjectManager = projectManager;

    myElement = projectManager.getDefaultProjectRootElement();
  }

  @Nullable
  Element getStateCopy() {
    final Element element = myProjectManager.getDefaultProjectRootElement();
    return element != null ? (Element)element.clone() : null;
  }


  @Override
  protected StateStorageManager createStateStorageManager() {
    Document _d = null;

    if (myElement != null) {
      myElement.detach();
      _d = new Document(myElement);
    }

    final PathMacroManager pathMacroManager = PathMacroManager.getInstance(getComponentManager());

    final Document document = _d;

    final XmlElementStorage storage = new XmlElementStorage(pathMacroManager.createTrackingSubstitutor()) {
      @Nullable
      protected Document loadDocument() throws StateStorage.StateStorageException {
        return document;
      }

      public List<VirtualFile> getAllStorageFiles() {
        return Collections.emptyList();
      }

      @Nullable
      Element getRootElement() throws StateStorageException {
        if (myElement == null) return null;
        return super.getRootElement();
      }

      protected SaveSession createSaveSession(final MyExternalizationSession externalizationSession) {
        return new DefaultSaveSession(externalizationSession);
      }

      class DefaultSaveSession extends MySaveSession {
        public DefaultSaveSession(MyExternalizationSession externalizationSession) {
          super(externalizationSession);
        }

        protected boolean _needsSave() throws StateStorageException {
          return true;
        }

        protected void doSave() throws StateStorageException {
          if (myElement != null) {
            myProjectManager.setDefaultProjectRootElement((Element)myElement.clone());
          }
        }
      }
    };

    return new StateStorageManager() {
      public void addMacro(String macro, String expansion) {
        throw new UnsupportedOperationException("Method addMacro not implemented in " + getClass());
      }

      @Nullable
      public StateStorage getStateStorage(@NotNull Storage storageSpec) throws StateStorage.StateStorageException {
        return storage;
      }

      @Nullable
      public StateStorage getFileStateStorage(String fileName) {
        return storage;
      }

      public void clearStateStorage(@NotNull String file) {
      }

      public List<VirtualFile> getAllStorageFiles() {
        return Collections.EMPTY_LIST;
      }

      public ExternalizationSession startExternalization() {
        return new MyExternalizationSession(storage);
      }

      public SaveSession startSave(final ExternalizationSession externalizationSession) throws StateStorage.StateStorageException {
        return new MySaveSession(storage, externalizationSession);
      }

      public void finishSave(SaveSession saveSession) {
        storage.finishSave(((MySaveSession)saveSession).saveSession);
      }

      @Nullable
      public StateStorage getOldStorage(Object component, final String componentName, final StateStorageOperation operation)
      throws StateStorage.StateStorageException {
        return storage;
      }
    };
  }

  public void setStorageFormat(final StorageFormat storageFormat) {
    throw new UnsupportedOperationException("Method setStorageFormat not implemented in " + getClass());
  }

  public String getLocation() {
    throw new UnsupportedOperationException("Method getLocation not implemented in " + getClass());
  }

  public void load() throws IOException {
    if (myElement == null) return;
    super.load();
  }

  private static class MyExternalizationSession implements StateStorageManager.ExternalizationSession {
    StateStorage.ExternalizationSession externalizationSession;

    public MyExternalizationSession(final XmlElementStorage storage) {
      externalizationSession = storage.startExternalization();
    }

    public void setState(@NotNull final Storage[] storageSpecs, final Object component, final String componentName, final Object state)
    throws StateStorage.StateStorageException {
      externalizationSession.setState(component, componentName, state, null);
    }

    public void setStateInOldStorage(final Object component, final String componentName, final Object state) throws StateStorage.StateStorageException {
      externalizationSession.setState(component, componentName, state, null);
    }
  }

  private static class MySaveSession implements StateStorageManager.SaveSession {
    StateStorage.SaveSession saveSession;

    public MySaveSession(final XmlElementStorage storage, final StateStorageManager.ExternalizationSession externalizationSession) {
      saveSession = storage.startSave(((MyExternalizationSession)externalizationSession).externalizationSession);
    }

    public List<VirtualFile> getAllStorageFilesToSave() throws StateStorage.StateStorageException {
      return Collections.EMPTY_LIST;
    }

    public void save() throws StateStorage.StateStorageException {
      saveSession.save();
    }

    public Set<String> getUsedMacros() throws StateStorage.StateStorageException {
      return Collections.EMPTY_SET;
    }
  }
}
