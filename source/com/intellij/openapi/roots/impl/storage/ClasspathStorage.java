package com.intellij.openapi.roots.impl.storage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

  protected CachedXmlDocumentSet myFileCache;

  @Nullable
  public <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto)
    throws StateStorageException {
    assert component instanceof ModuleRootManager;
    assert componentName.equals("NewModuleRootManager");
    assert stateClass == ModuleRootManagerImpl.ModuleRootManagerState.class;

    try {
      final Module module = ((ModuleRootManagerImpl)component).getModule();
      final Element element = new Element(ClasspathStorage.COMPONENT_TAG);
      createFileCache(module);
      getClasspath(module, element);
      PathMacroManager.getInstance(module).expandPaths(element);
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
      final Module module = ((ModuleRootManagerImpl)component).getModule();
      final Element element = new Element(ClasspathStorage.COMPONENT_TAG);
      ((ModuleRootManagerImpl.ModuleRootManagerState)state).writeExternal(element);
      PathMacroManager.getInstance(module).collapsePaths(element);
      createFileCache(module);
      setClasspath(module, element);
    }
    catch (WriteExternalException e) {
      throw new StateStorageException(e);
    }
    catch (IOException e) {
      throw new StateStorageException(e);
    }
  }

  public List<VirtualFile> getAllStorageFiles() {
    final List<VirtualFile> list = new ArrayList<VirtualFile>();
    myFileCache.listFiles(list);
    return list;
  }

  public boolean needsSave() throws StateStorageException {
    return myFileCache.hasChanged();
  }

  public void save() throws StateStorageException {
    final Ref<IOException> ref = new Ref<IOException>();
      ApplicationManager.getApplication().runWriteAction(new Runnable(){
        public void run() {
          try {
            myFileCache.commit();
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

  private void createFileCache(final Module module) throws StateStorageException, IOException {
    if (myFileCache == null) {
      myFileCache = new CachedXmlDocumentSet();
      registerFiles(module, getModuleDir(module), getStorageRootFromOptions(module));
    }
  }

  private static String getStorageRootFromOptions(final Module module) {
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

  public static boolean checkCompatibility(final ModifiableRootModel model, final String storageID) {
    if (EclipseClasspathStorage.ID.equals(storageID) && !EclipseClasspathStorage.checkCompatibility(model)) {
      return false;
    }
    return true;
  }

  protected static String getModuleDir(final Module module) {
    return new File(module.getModuleFilePath()).getParent();
  }

  @Nullable
  private static String getStorageRoot(final Module module, final Module moduleBeingLoaded ) {
    if ( module == moduleBeingLoaded ) {
      return getStorageRootFromOptions(module);
    } else {
      final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      return contentRoots.length == 0 ? null : contentRoots[0].getPath();
    }
  }

  protected static Map<String, String> getStorageRootMap(final Project project, final Module moduleBeingLoaded) {
    final Map<String, String> map = new HashMap<String, String>();
    for (Module aModule : ModuleManager.getInstance(project).getModules()) {
      final String storageRoot = getStorageRoot(aModule, moduleBeingLoaded);
      if ( storageRoot != null ) {
        map.put(aModule.getName(), PathUtil.normalize(storageRoot));
      }
    }
    return map;
  }

  protected abstract void registerFiles(final Module module, final String moduleRoot, final String contentRoot ) throws IOException;

  protected abstract void getClasspath(final Module module, Element element) throws IOException, InvalidDataException;

  protected abstract void setClasspath(Module module, Element element) throws IOException, WriteExternalException;
}
