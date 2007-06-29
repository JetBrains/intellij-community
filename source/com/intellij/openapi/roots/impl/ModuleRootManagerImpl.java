package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


@State(
  name = "NewModuleRootManager",
  storages = {
    @Storage(
      id = ClasspathStorage.DEFAULT_STORAGE,
      file = "$MODULE_FILE$"
    ),

    @Storage(
          id = ClasspathStorage.SPECIAL_STORAGE,
          storageClass = ClasspathStorage.class
    )
  },
  storageChooser = ModuleRootManagerImpl.StorageChooser.class
)
public class ModuleRootManagerImpl extends ModuleRootManager implements ModuleComponent, PersistentStateComponent<ModuleRootManagerImpl.ModuleRootManagerState> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.ModuleRootManagerImpl");

  private final Module myModule;
  private final ProjectRootManagerImpl myProjectRootManager;
  private final VirtualFilePointerManager myFilePointerManager;
  private RootModelImpl myRootModel;
  private final ModuleFileIndexImpl myFileIndex;
  private boolean myIsDisposed = false;
  private boolean isModuleAdded = false;
  private final Map<OrderRootType, Set<VirtualFilePointer>> myCachedFiles;
  private final Map<OrderRootType, Set<VirtualFilePointer>> myCachedExportedFiles;

  @NonNls private static final String LANGUAGE_LEVEL_ELEMENT_NAME = "LANGUAGE_LEVEL";
  @Nullable private LanguageLevel myLanguageLevel;

  public ModuleRootManagerImpl(Module module,
                               DirectoryIndex directoryIndex,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) {
    myModule = module;
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;

    myFileIndex = new ModuleFileIndexImpl(myModule, directoryIndex);
    myCachedFiles = new THashMap<OrderRootType, Set<VirtualFilePointer>>();
    myCachedExportedFiles = new THashMap<OrderRootType, Set<VirtualFilePointer>>();
    myRootModel = new RootModelImpl(this, myProjectRootManager, myFilePointerManager);
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public ModuleFileIndex getFileIndex() {
    return myFileIndex;
  }

  @NotNull
  public String getComponentName() {
    return "NewModuleRootManager";
  }

  public void initComponent() { }

  public void disposeComponent() {
    myRootModel.disposeModel();
    myIsDisposed = true;
  }

  public VirtualFile getCompilerOutputPath() {
    return myRootModel.getCompilerOutputPath();
  }

  public String getCompilerOutputPathUrl() {
    return myRootModel.getCompilerOutputUrl();
  }

  public VirtualFile getCompilerOutputPathForTests() {
    return myRootModel.getCompilerOutputPathForTests();
  }

  public String getCompilerOutputPathForTestsUrl() {
    return myRootModel.getCompilerOutputUrlForTests();
  }

  public boolean isJdkInherited() {
    return myRootModel.isJdkInherited();
  }

  public boolean isCompilerOutputPathInherited() {
    return myRootModel.isCompilerOutputPathInherited();
  }

  public ProjectJdk getJdk() {
    return myRootModel.getJdk();
  }

  public VirtualFile getExplodedDirectory() {
    return myRootModel.getExplodedDirectory();
  }

  public String getExplodedDirectoryUrl() {
    return myRootModel.getExplodedDirectoryUrl();
  }

  @NotNull
  public ModifiableRootModel getModifiableModel() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return new RootModelImpl(myRootModel, this, true, null, myFilePointerManager, myProjectRootManager);
  }

  void fireBeforeRootsChange() {
    if (!isModuleAdded) return;

    // IMPORTANT: should be the first listener!
    ((ProjectRootManagerImpl)ProjectRootManager.getInstance(myModule.getProject())).beforeRootsChange(false);
  }

  void fireRootsChanged() {
    if (!isModuleAdded) return;

    ((ProjectRootManagerImpl)ProjectRootManager.getInstance(myModule.getProject())).rootsChanged(false);
  }


  RootModelImpl getRootModel() {
    return myRootModel;
  }

  public ContentEntry[] getContentEntries() {
    return myRootModel.getContentEntries();
  }

  @NotNull
  public OrderEntry[] getOrderEntries() {
    return myRootModel.getOrderEntries();
  }

  @NotNull
  public VirtualFile[] getFiles(OrderRootType type) {
    return getFiles(type, new HashSet<Module>());
  }

  @NotNull
  private VirtualFile[] getFiles(OrderRootType type, Set<Module> processed) {
    Set<VirtualFilePointer> cachedFiles = myCachedFiles.get(type);
    if (cachedFiles == null) {
      cachedFiles = new THashSet<VirtualFilePointer>();
      final Iterator orderIterator = myRootModel.getOrderIterator();
      while (orderIterator.hasNext()) {
        OrderEntry entry = (OrderEntry)orderIterator.next();
        final String [] urls;
        if (entry instanceof ModuleOrderEntryImpl) {
          urls = ((ModuleOrderEntryImpl)entry).getUrls(type, processed);
        }
        else {
          urls = entry.getUrls(type);
        }
        final VirtualFilePointerManager virtualFilePointerManager = VirtualFilePointerManager.getInstance();
        for (String url : urls) {
          if (url != null) {
            cachedFiles.add(virtualFilePointerManager.create(url, null));
          }
        }
      }
      myCachedFiles.put(type, cachedFiles);
    }
    return convertPointers(cachedFiles);
  }

  private static VirtualFile[] convertPointers(final Set<VirtualFilePointer> cachedFiles) {
    final LinkedHashSet<VirtualFile> result = new LinkedHashSet<VirtualFile>();
    for (VirtualFilePointer cachedFile : cachedFiles) {
      final VirtualFile virtualFile = cachedFile.getFile();
      if (virtualFile != null) {
        result.add(virtualFile);
      }
    }

    return result.toArray(new VirtualFile[result.size()]);
  }

  @NotNull
  public String[] getUrls(OrderRootType type) {
    return getUrls(type, new HashSet<Module>());
  }

  @NotNull private String[] getUrls(OrderRootType type, Set<Module> processed) {
    final ArrayList<String> result = new ArrayList<String>();
    final Iterator orderIterator = myRootModel.getOrderIterator();
    while (orderIterator.hasNext()) {
      final OrderEntry entry = (OrderEntry)orderIterator.next();
      final String[] urls;
      if (entry instanceof ModuleOrderEntryImpl) {
        urls = ((ModuleOrderEntryImpl)entry).getUrls(type, processed);
      }
      else {
        urls = entry.getUrls(type);
      }
      result.addAll(Arrays.asList(urls));
    }
    return result.toArray(new String[result.size()]);
  }

  void commitModel(RootModelImpl rootModel) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    LOG.assertTrue(rootModel.myModuleRootManager == this);

    final Project project = myModule.getProject();
    final ModifiableModuleModel moduleModel = ModuleManager.getInstance(project).getModifiableModel();
    multiCommit(new ModifiableRootModel[]{rootModel}, moduleModel);
  }

  private void commitModelWithoutEvents(RootModelImpl rootModel) {
    setModel(rootModel);
  }

  private void setModel(RootModelImpl rootModel) {
    if (myRootModel != rootModel) {
      myRootModel.disposeModel();
    }
    if (!isModuleAdded) {
      myRootModel = rootModel;
    }
    else {
      final VirtualFilePointerListener listener = ((ProjectRootManagerImpl)ProjectRootManager.getInstance(
        myModule.getProject())).getVirtualFilePointerListener();
      myRootModel = new RootModelImpl(rootModel, this, false, listener, myFilePointerManager, myProjectRootManager);
      rootModel.disposeModel();
    }
  }


  static void multiCommit(ModifiableRootModel[] _rootModels,
                          ModifiableModuleModel moduleModel) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    final List<RootModelImpl> modelsToCommit = getSortedChangedModels(_rootModels, moduleModel);

    final List<ModifiableRootModel> modelsToDispose = new ArrayList<ModifiableRootModel>(Arrays.asList(_rootModels));
    modelsToDispose.removeAll(modelsToCommit);

    Runnable runnable = new Runnable() {
      public void run() {
        for (RootModelImpl rootModel : modelsToCommit) {
          rootModel.myModuleRootManager.commitModelWithoutEvents(rootModel);
        }

        for (ModifiableRootModel model : modelsToDispose) {
          model.dispose();
        }
      }
    };
    ModuleManagerImpl.commitModelWithRunnable(moduleModel, runnable);

  }

  private static List<RootModelImpl> getSortedChangedModels(ModifiableRootModel[] _rootModels,
                                                    final ModifiableModuleModel moduleModel) {
    List<RootModelImpl> rootModels = new ArrayList<RootModelImpl>();
    for (ModifiableRootModel _rootModel : _rootModels) {
      RootModelImpl rootModel = (RootModelImpl)_rootModel;
      if (rootModel.isChanged()) {
        rootModels.add(rootModel);
      }
    }

    sortRootModels(rootModels, moduleModel);
    return rootModels;
  }

  @NotNull
  public Module[] getDependencies() {
    return myRootModel.getModuleDependencies();
  }

  public boolean isDependsOn(Module module) {
    return myRootModel.isDependsOn(module);
  }

  @NotNull
  public String[] getDependencyModuleNames() {
    return myRootModel.getDependencyModuleNames();
  }

  public <R> R processOrder(RootPolicy<R> policy, R initialValue) {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.processOrder(policy, initialValue);
  }

  String[] getUrlsForOtherModules(OrderRootType rootType, Set<Module> processed) {
    List<String> result = new ArrayList<String>();
    if (OrderRootType.SOURCES.equals(rootType) || OrderRootType.COMPILATION_CLASSES.equals(rootType)) {
      myRootModel.addExportedUrs(rootType, result, processed);
      return result.toArray(new String[result.size()]);
    }
    else if (OrderRootType.JAVADOC.equals(rootType)) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    else if (OrderRootType.CLASSES.equals(rootType)) {
      myRootModel.addExportedUrs(rootType, result, processed);
      return result.toArray(new String[result.size()]);
    }
    else if (OrderRootType.CLASSES_AND_OUTPUT.equals(rootType)) {
      return getUrls(OrderRootType.CLASSES_AND_OUTPUT, processed);
    }
    else if (OrderRootType.ANNOTATIONS.equals(rootType)) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    LOG.error("Unknown root type: " + rootType);
    return null;

    /*
    if (OrderRootType.SOURCES.equals(rootType)) {
      return ModuleRootManagerImpl.this.getSourceRootUrls();
    }
    else if (OrderRootType.JAVADOC.equals(rootType)) {
      return new String[0];
    }
    else if (OrderRootType.CLASSES.equals(rootType)) {
      return ModuleRootManagerImpl.this.getUrls(OrderRootType.CLASSES);
    }
    else if (OrderRootType.CLASSES_AND_OUTPUT.equals(rootType)) {
      return ModuleRootManagerImpl.this.getUrls(OrderRootType.CLASSES_AND_OUTPUT);
    }
    else if (OrderRootType.COMPILATION_CLASSES.equals(rootType)) {
      final ArrayList<String> result = new ArrayList<String>();
      if (getCompilerOutputPathUrl() != null) {
        result.add(getCompilerOutputPathUrl());
      }
      if (getCompilerOutputPathForTestsUrl() != null) {
        result.add(getCompilerOutputPathForTestsUrl());
      }
      return (String[])result.toArray(new String[result.size()]);
    }
    LOG.error("Unknown root type: " + rootType);
    return null;
    */
  }

  @NotNull
  VirtualFile[] getFilesForOtherModules(OrderRootType rootType, Set<Module> processed) {
    Set<VirtualFilePointer> files = myCachedExportedFiles.get(rootType);
    if (files == null) {
      files = new THashSet<VirtualFilePointer>();
      List<String> result = new ArrayList<String>();
      if (OrderRootType.SOURCES.equals(rootType) || OrderRootType.COMPILATION_CLASSES.equals(rootType)) {
        myRootModel.addExportedUrs(rootType, result, processed);
      }
      else if (OrderRootType.CLASSES.equals(rootType)) {
        myRootModel.addExportedUrs(rootType, result, processed);
      }
      else if (OrderRootType.CLASSES_AND_OUTPUT.equals(rootType)) {
        return getFiles(OrderRootType.CLASSES_AND_OUTPUT, processed);
      }
      else if (OrderRootType.JAVADOC.equals(rootType)) {
        return getFiles(OrderRootType.JAVADOC, processed);
      }
      else if (OrderRootType.ANNOTATIONS.equals(rootType)) {
        return getFiles(OrderRootType.ANNOTATIONS, processed);
      }
      else {
        LOG.error("Unknown root type: " + rootType);
        return null;
      }
      final VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();
      for (String url : result) {
        if (url != null) {
          files.add(pointerManager.create(url, null));
        }
      }
      myCachedExportedFiles.put(rootType, files);
    }

    return convertPointers(files);
  }

  @NotNull
  public VirtualFile[] getContentRoots() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getContentRoots();
  }

  @NotNull
  public String[] getContentRootUrls() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getContentRootUrls();
  }

  @NotNull
  public String[] getExcludeRootUrls() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getExcludeRootUrls();
  }

  @NotNull
  public VirtualFile[] getExcludeRoots() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getExcludeRoots();
  }

  @NotNull
  public String[] getSourceRootUrls() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getSourceRootUrls();
  }

  @NotNull
  public VirtualFile[] getSourceRoots() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getSourceRoots();
  }

  public void projectOpened() {
    final ArrayList<RootModelComponentBase> components = myRootModel.myComponents;
    for (RootModelComponentBase rootModelComponentBase : components) {
      rootModelComponentBase.projectOpened();
    }
  }

  public void projectClosed() {
    final ArrayList<RootModelComponentBase> components = myRootModel.myComponents;
    for (RootModelComponentBase rootModelComponentBase : components) {
      rootModelComponentBase.projectClosed();
    }
  }

  public void moduleAdded() {
    RootModelImpl oldModel = myRootModel;
    final VirtualFilePointerListener listener = ((ProjectRootManagerImpl)ProjectRootManager.getInstance(
      myModule.getProject())).getVirtualFilePointerListener();
    myRootModel = new RootModelImpl(myRootModel, this, false, listener, myFilePointerManager, myProjectRootManager);
    oldModel.disposeModel();
    final ArrayList<RootModelComponentBase> components = myRootModel.myComponents;
    for (RootModelComponentBase rootModelComponentBase : components) {
      rootModelComponentBase.moduleAdded();
    }
    isModuleAdded = true;
  }


  private static void sortRootModels(List<RootModelImpl> rootModels, final ModifiableModuleModel moduleModel) {
    DFSTBuilder<RootModelImpl> builder = createDFSTBuilder(rootModels, moduleModel);

    final Comparator<RootModelImpl> comparator = builder.comparator();
    Collections.sort(rootModels, comparator);
  }

  private static DFSTBuilder<RootModelImpl> createDFSTBuilder(List<RootModelImpl> rootModels, final ModifiableModuleModel moduleModel) {
    final Map<String, RootModelImpl> nameToModel = new HashMap<String, RootModelImpl>();
    for (final RootModelImpl rootModel : rootModels) {
      final String name = rootModel.getModule().getName();
      LOG.assertTrue(!nameToModel.containsKey(name));
      nameToModel.put(name, rootModel);
    }
    final Module[] modules = moduleModel.getModules();
    for (final Module module : modules) {
      final String name = module.getName();
      if (!nameToModel.containsKey(name)) {
        final RootModelImpl rootModel = ((ModuleRootManagerImpl)ModuleRootManager.getInstance(module)).myRootModel;
        nameToModel.put(name, rootModel);
      }
    }
    final Collection<RootModelImpl> allRootModels = nameToModel.values();
    return new DFSTBuilder<RootModelImpl>(new GraphGenerator<RootModelImpl>(new CachingSemiGraph<RootModelImpl>(new GraphGenerator.SemiGraph<RootModelImpl>() {
          public Collection<RootModelImpl> getNodes() {
            return allRootModels;
          }

          public Iterator<RootModelImpl> getIn(RootModelImpl rootModel) {
            final ArrayList<String> names1 = rootModel.processOrder(new RootPolicy<ArrayList<String>>() {
              public ArrayList<String> visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, ArrayList<String> strings) {
                final Module module = moduleOrderEntry.getModule();
                if (module != null) {
                  strings.add(module.getName());
                } else {
                  final Module moduleToBeRenamed = moduleModel.getModuleToBeRenamed(moduleOrderEntry.getModuleName());
                  if (moduleToBeRenamed != null) {
                    strings.add(moduleToBeRenamed.getName());
                  }
                }
                return strings;
              }
            }, new ArrayList<String>());

            final String[] names = names1.toArray(new String[names1.size()]);
            List<RootModelImpl> result = new ArrayList<RootModelImpl>();
            for (String name : names) {
              final RootModelImpl depRootModel = nameToModel.get(name);
              if (depRootModel != null) { // it is ok not to find one
                result.add(depRootModel);
              }
            }
            return result.iterator();
          }
        })));
  }


  @NotNull
  public VirtualFile[] getJavadocPaths() {
    return myRootModel.getJavadocPaths();
  }

  @NotNull
  public String[] getJavadocUrls() {
    return myRootModel.getJavadocUrls();
  }

  @NotNull
  public VirtualFile[] getAnnotationPaths() {
    return myRootModel.getAnnotationPaths();
  }

  @NotNull
  public String[] getAnnotationUrls() {
    return myRootModel.getAnnotationUrls();
  }

  public void dropCaches() {
    VirtualFilePointerManager manager = VirtualFilePointerManager.getInstance();
    killPointers(manager, myCachedFiles);
    killPointers(manager, myCachedExportedFiles);
    myCachedFiles.clear();
    myCachedExportedFiles.clear();
  }

  private static void killPointers(final VirtualFilePointerManager manager, Map<OrderRootType, Set<VirtualFilePointer>> cach) {
    for (Set<VirtualFilePointer> pointers : cach.values()) {
      for (VirtualFilePointer pointer : pointers) {
        manager.kill(pointer, null);
      }
    }
  }

  static void checkCircularDependencies(ModifiableRootModel[] _rootModels, ModifiableModuleModel moduleModel)
    throws ModuleCircularDependencyException {
    List<RootModelImpl> rootModels = new ArrayList<RootModelImpl>();
    for (ModifiableRootModel _rootModel : _rootModels) {
      RootModelImpl rootModel = (RootModelImpl)_rootModel;
      if (rootModel.isChanged()) {
        rootModels.add(rootModel);
      }
    }
    DFSTBuilder<RootModelImpl> dfstBuilder = createDFSTBuilder(rootModels, moduleModel);
    Pair<RootModelImpl, RootModelImpl> circularDependency = dfstBuilder.getCircularDependency();
    if (circularDependency != null) {
      throw new ModuleCircularDependencyException(circularDependency.first.getModule().getName(),
                                                  circularDependency.second.getModule().getName());
    }
  }

  public void setLanguageLevel(@Nullable LanguageLevel languageLevel) {
    boolean needToReload = myLanguageLevel != languageLevel;
    myLanguageLevel = languageLevel;
    if (needToReload && myModule.getProject().isOpen()) {
      myProjectRootManager.reloadProjectOnLanguageLevelChange(languageLevel, true);
    }
  }

  @Nullable
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public ModuleRootManagerState getState() {
    return new ModuleRootManagerState(myRootModel, getLanguageLevel());
  }

  public void loadState(ModuleRootManagerState object) {
    try {
      final RootModelImpl newModel = new RootModelImpl(object.getRootModelElement(), this, myProjectRootManager, myFilePointerManager);

      boolean throwEvent = myRootModel != null;

      if (throwEvent) {
        fireBeforeRootsChange();
        myLanguageLevel = object.getLanguageLevel();
        setModel(newModel);
        final ArrayList<RootModelComponentBase> components = myRootModel.myComponents;
        for (RootModelComponentBase rootModelComponentBase : components) {
          rootModelComponentBase.moduleAdded();
        }
        fireRootsChanged();
      }
      else {
        myLanguageLevel = object.getLanguageLevel();
        myRootModel = newModel;
      }
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public static class ModuleRootManagerState implements JDOMExternalizable {
    private RootModelImpl myRootModel;
    private LanguageLevel myLanguageLevel;
    private Element myRootModelElement = null;


    public ModuleRootManagerState() {
    }

    public ModuleRootManagerState(final RootModelImpl rootModel, final LanguageLevel languageLevel) {
      myRootModel = rootModel;
      myLanguageLevel = languageLevel;
    }

    public void readExternal(Element element) throws InvalidDataException {
      final Attribute langLevelAttribute = element.getAttribute(LANGUAGE_LEVEL_ELEMENT_NAME);
      if (langLevelAttribute != null) {
        try {
          myLanguageLevel = LanguageLevel.valueOf(langLevelAttribute.getValue());
        }
        catch (IllegalArgumentException e) {
          //bad value was stored
        }
      }

      myRootModelElement = element;
    }

    public void writeExternal(Element element) throws WriteExternalException {
      if (myLanguageLevel != null) {
        element.setAttribute(LANGUAGE_LEVEL_ELEMENT_NAME, myLanguageLevel.toString());
      }
      myRootModel.writeExternal(element);
    }

    public LanguageLevel getLanguageLevel() {
      return myLanguageLevel;
    }

    public Element getRootModelElement() {
      return myRootModelElement;
    }
  }

  public static class StorageChooser implements StateStorageChooser<ModuleRootManagerImpl> {
    public Storage[] selectStorages(Storage[] storages, ModuleRootManagerImpl moduleRootManager, final StateStorageOperation operation) {
      final String storageType = ClasspathStorage.getStorageType(moduleRootManager.getModule());
      final String id = storageType.equals(ClasspathStorage.DEFAULT_STORAGE)? ClasspathStorage.DEFAULT_STORAGE: ClasspathStorage.SPECIAL_STORAGE;
      for (Storage storage : storages) {
        if (storage.id().equals(id)) return new Storage[]{storage};
      }
      throw new IllegalArgumentException();
    }
  }
}