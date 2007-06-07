package com.intellij.openapi.roots.impl.storage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.eclipse.config.EclipseClasspathStorage;
import com.intellij.projectImport.eclipse.util.PathUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
* User: Vladislav.Kaznacheev
* Date: Mar 9, 2007
* Time: 1:42:06 PM
*/
abstract public class ClasspathStorage implements StateStorage {

  @NonNls public static final String DEFAULT_STORAGE = "default";
  public static final String DEFAULT_STORAGE_DESCR = ProjectBundle.message("project.roots.classpath.format.default.descr");

  @NonNls private static final String CLASSPATH_OPTION = "classpath";
  @NonNls private static final String CLASSPATH_DIR_OPTION = "classpath-dir";

  @NonNls private static final String COMPONENT_TAG = "component";

  private Module myModule;
  private Object mySession;

  @Nullable
  public <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto)
    throws StateStorageException {
    assert component instanceof ModuleRootManager;
    assert componentName.equals("NewModuleRootManager");
    assert stateClass == ModuleRootManagerImpl.ModuleRootManagerState.class;

    try {
      myModule = ((ModuleRootManagerImpl)component).getModule();
      final Element element = new Element(ClasspathStorage.COMPONENT_TAG);
      getClasspath(myModule, element);
      PathMacroManager.getInstance(myModule).expandPaths(element);
      ModuleRootManagerImpl.ModuleRootManagerState moduleRootManagerState = new ModuleRootManagerImpl.ModuleRootManagerState();
      moduleRootManagerState.readExternal(element);
      //noinspection unchecked
      return (T)moduleRootManagerState;
    }
    catch (InvalidDataException e) {
      throw new StateStorageException(e);
    }
    catch (IOException e) {
      throw new StateStorageException(e);
    }
  }

  public void setState(Object component, final String componentName, Object state) throws StateStorageException {
    assert component instanceof ModuleRootManager;
    assert componentName.equals("NewModuleRootManager");
    assert state.getClass() == ModuleRootManagerImpl.ModuleRootManagerState.class;

    try {
      myModule = ((ModuleRootManagerImpl)component).getModule();
      final Element element = new Element(ClasspathStorage.COMPONENT_TAG);
      ((ModuleRootManagerImpl.ModuleRootManagerState)state).writeExternal(element);
      PathMacroManager.getInstance(myModule).collapsePaths(element);
      setClasspath(myModule, element);
    }
    catch (WriteExternalException e) {
      throw new StateStorageException(e);
    }
    catch (IOException e) {
      throw new StateStorageException(e);
    }
  }

  @NotNull
  public ExternalizationSession startExternalization() {
    assert mySession == null;
    final ExternalizationSession session = new ExternalizationSession() {
      public void setState(final Object component, final String componentName, final Object state, final Storage storageSpec) throws StateStorageException {
        assert mySession == this;
        ClasspathStorage.this.setState(component, componentName, state);
      }
    };

    mySession = session;
    return session;
  }

  @NotNull
  public SaveSession startSave(final ExternalizationSession externalizationSession) {
    assert mySession == externalizationSession;

    final SaveSession session = new SaveSession() {
      public boolean needsSave() throws StateStorageException {
        assert mySession == this;
        return ClasspathStorage.this.needsSave();
      }

      public void save() throws StateStorageException {
        assert mySession == this;
        ClasspathStorage.this.save();
      }

      public Set<String> getUsedMacros() {
        assert mySession == this;
        return Collections.EMPTY_SET;
      }

      @Nullable
      public Set<String> analyzeExternalChanges(final Set<VirtualFile> changedFiles) {
        return null;
      }
    };

    mySession = session;
    return session;
  }

  public void finishSave(final SaveSession saveSession) {
    assert mySession == saveSession;
    mySession = null;
  }

  public List<VirtualFile> getAllStorageFiles() {
    final List<VirtualFile> list = new ArrayList<VirtualFile>();
    getFileSet(myModule).listFiles(list);
    return list;
  }

  public boolean needsSave() throws StateStorageException {
    return getFileSet(myModule).hasChanged();
  }

  public void save() throws StateStorageException {
    final Ref<IOException> ref = new Ref<IOException>();
      ApplicationManager.getApplication().runWriteAction(new Runnable(){
        public void run() {
          try {
            getFileSet(myModule).commit();
          }
          catch (IOException e) {
            ref.set(e);
          }
        }
      });

    if (!ref.isNull()) {
      throw new StateStorageException(ref.get());
    }
  }

  protected static String getStorageRootFromOptions(final Module module) {
    final String moduleRoot = getModuleDir(module);
    final String storageRef = module.getOptionValue(CLASSPATH_DIR_OPTION);
    return storageRef != null ? PathUtil.concatPath(moduleRoot,storageRef) : moduleRoot;
  }

  @NotNull
  public static String getStorageType(final Module module) {
    final String id = module.getOptionValue(CLASSPATH_OPTION);
    return id != null ? id : DEFAULT_STORAGE;
  }

  public static void setStorageType(final Module module, final String storageID) {
    final String oldStorageType = getStorageType(module);
    if ( oldStorageType.equals(storageID)) {
      return;
    }

    if (EclipseClasspathStorage.ID.equals(oldStorageType) ){
        EclipseClasspathStorage.closeFileSet(module);
    }

    if ( storageID.equals(DEFAULT_STORAGE)) {
      module.clearOption(CLASSPATH_OPTION);
      module.clearOption(CLASSPATH_DIR_OPTION);
    } else {
      module.setOption(CLASSPATH_OPTION, storageID);

      final String relPath = PathUtil.getRelative(getModuleDir(module), getStorageRoot(module, null));
      if (!relPath.equals(".")) {
        module.setOption(CLASSPATH_DIR_OPTION, relPath);
      }
    }
  }

  public static void assertCompatible(final ModifiableRootModel model, final String storageID) throws ConfigurationException {
    if (EclipseClasspathStorage.ID.equals(storageID) ){
        EclipseClasspathStorage.assertCompatible (model);
    }
  }

  protected static String getModuleDir(final Module module) {
    return new File(module.getModuleFilePath()).getParent();
  }

  @Nullable
  public static String getStorageRoot(final Module module, final Module moduleBeingLoaded ) {
    if ( module == moduleBeingLoaded ) {
      return getStorageRootFromOptions(module);
    } else {
      final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      return contentRoots.length == 0 ? null : contentRoots[0].getPath();
    }
  }

  public static Map<String, String> getStorageRootMap(final Project project, final Module moduleBeingLoaded) {
    final Map<String, String> map = new HashMap<String, String>();
    for (Module aModule : ModuleManager.getInstance(project).getModules()) {
      final String storageRoot = getStorageRoot(aModule, moduleBeingLoaded);
      if ( storageRoot != null ) {
        map.put(aModule.getName(), PathUtil.normalize(storageRoot));
      }
    }
    return map;
  }

  protected abstract FileSet getFileSet(final Module module);

  protected abstract void getClasspath(final Module module, Element element) throws IOException, InvalidDataException;

  protected abstract void setClasspath(final Module module, Element element) throws IOException, WriteExternalException;
}
