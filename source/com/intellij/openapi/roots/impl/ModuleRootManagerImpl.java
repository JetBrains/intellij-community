package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;

import java.util.*;

/**
 * @author dsl
 */
public class ModuleRootManagerImpl extends ModuleRootManager implements ModuleComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.ModuleRootManagerImpl");

  private final Module myModule;
  private final ProjectRootManagerImpl myProjectRootManager;
  private final VirtualFilePointerManager myFilePointerManager;
  private RootModelImpl myRootModel;
  private final ModuleFileIndexImpl myFileIndex;
  private boolean myIsDisposed = false;
  private boolean isModuleAdded = false;

  private Map<OrderRootType, VirtualFile[]> myCachedFiles;

  public ModuleRootManagerImpl(Module module,
                               DirectoryIndex directoryIndex,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) {
    myModule = module;
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;

    myFileIndex = new ModuleFileIndexImpl(myModule, directoryIndex);
    myCachedFiles = new HashMap<OrderRootType, VirtualFile[]>();
    myRootModel = new RootModelImpl(this, myProjectRootManager, myFilePointerManager);
  }

  public Module getModule() {
    return myModule;
  }

  public ModuleFileIndex getFileIndex() {
    return myFileIndex;
  }

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

  public ProjectJdk getJdk() {
    return myRootModel.getJdk();
  }

  public VirtualFile getExplodedDirectory() {
    return myRootModel.getExplodedDirectory();
  }

  public String getExplodedDirectoryUrl() {
    return myRootModel.getExplodedDirectoryUrl();
  }

  public void readExternal(Element element) throws InvalidDataException {
    setModel(new RootModelImpl(element, this, myProjectRootManager, myFilePointerManager));
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myRootModel.writeExternal(element);
  }

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

  public OrderEntry[] getOrderEntries() {
    return myRootModel.getOrderEntries();
  }

  public VirtualFile[] getFiles(OrderRootType type) {
    return getFiles(type, new HashSet<Module>());
  }

  private VirtualFile[] getFiles(OrderRootType type, Set<Module> processed) {
    VirtualFile[] cachedFiles = myCachedFiles.get(type);
    if (cachedFiles == null) {
      final LinkedHashSet<VirtualFile> result = new LinkedHashSet<VirtualFile>();
      final Iterator orderIterator = myRootModel.getOrderIterator();
      while (orderIterator.hasNext()) {
        OrderEntry entry = (OrderEntry)orderIterator.next();
        final VirtualFile[] files;
        if (entry instanceof ModuleOrderEntry) {
          files = ((ModuleOrderEntryImpl)entry).getFiles(type, processed);
        }
        else {
          files = entry.getFiles(type);
        }
        for (int i = 0; i < files.length; i++) {
          VirtualFile file = files[i];
          if (file != null) {
            result.add(file);
          }
        }
      }
      cachedFiles = result.toArray(new VirtualFile[result.size()]);
      myCachedFiles.put(type, cachedFiles);
    }
    return cachedFiles;
  }

  public String[] getUrls(OrderRootType type) {
    return getUrls(type, new HashSet<Module>());
  }

  private String[] getUrls(OrderRootType type, Set<Module> processed) {
    final ArrayList<String> result = new ArrayList<String>();
    final Iterator orderIterator = myRootModel.getOrderIterator();
    while (orderIterator.hasNext()) {
      final OrderEntry entry = (OrderEntry)orderIterator.next();
      final String[] urls;
      if (entry instanceof ModuleOrderEntry) {
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
    multiCommit(project, new ModifiableRootModel[]{rootModel}, moduleModel);  
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


  static void multiCommit(Project project,
                          ModifiableRootModel[] _rootModels,
                          ModifiableModuleModel moduleModel) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    final List<RootModelImpl> modelsToCommit = getSortedChangedModels(_rootModels, moduleModel);

    final List<ModifiableRootModel> modelsToDispose = new ArrayList<ModifiableRootModel>(Arrays.asList(_rootModels));
    modelsToDispose.removeAll(modelsToCommit);

    Runnable runnable = new Runnable() {
      public void run() {
        for (int i = 0; i < modelsToCommit.size(); i++) {
          RootModelImpl rootModel = modelsToCommit.get(i);
          rootModel.myModuleRootManager.commitModelWithoutEvents(rootModel);
        }

        for (int i = 0; i < modelsToDispose.size(); i++) {
          ModifiableRootModel model = modelsToDispose.get(i);
          model.dispose();
        }
      }
    };
    ((ModuleManagerImpl)ModuleManager.getInstance(project)).commitModelWithRunnable(moduleModel, runnable);

  }

  static List<RootModelImpl> getSortedChangedModels(ModifiableRootModel[] _rootModels,
                                                     final ModifiableModuleModel moduleModel) {
    List<RootModelImpl> rootModels = new ArrayList<RootModelImpl>();
    for (int i = 0; i < _rootModels.length; i++) {
      RootModelImpl rootModel = (RootModelImpl)_rootModels[i];
      if (rootModel.isChanged()) {
        rootModels.add((RootModelImpl)rootModel);
      }
    }

    sortRootModels(rootModels, moduleModel);
    return rootModels;
  }

  public Module[] getDependencies() {
    return myRootModel.getModuleDependencies();
  }

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
      return ModuleRootManagerImpl.this.getUrls(OrderRootType.CLASSES_AND_OUTPUT, processed);
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


  VirtualFile[] getFilesForOtherModules(OrderRootType rootType, Set<Module> processed) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    if (OrderRootType.SOURCES.equals(rootType) || OrderRootType.COMPILATION_CLASSES.equals(rootType)) {
      myRootModel.addExportedFiles(rootType, result, processed);
      return result.toArray(new VirtualFile[result.size()]);
    }
    else if (OrderRootType.JAVADOC.equals(rootType)) {
      return VirtualFile.EMPTY_ARRAY;
    }
    else if (OrderRootType.CLASSES.equals(rootType)) {
      myRootModel.addExportedFiles(rootType, result, processed);
      return result.toArray(new VirtualFile[result.size()]);
    }
    else if (OrderRootType.CLASSES_AND_OUTPUT.equals(rootType)) {
      return ModuleRootManagerImpl.this.getFiles(OrderRootType.CLASSES_AND_OUTPUT, processed);
    }
    LOG.error("Unknown root type: " + rootType);
    return null;
  }

  public VirtualFile[] getContentRoots() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getContentRoots();
  }

  public String[] getContentRootUrls() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getContentRootUrls();
  }

  public String[] getExcludeRootUrls() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getExcludeRootUrls();
  }

  public VirtualFile[] getExcludeRoots() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getExcludeRoots();
  }

  public String[] getSourceRootUrls() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getSourceRootUrls();
  }

  public VirtualFile[] getSourceRoots() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getSourceRoots();
  }

  public void projectOpened() {
    final ArrayList<RootModelComponentBase> components = myRootModel.myComponents;
    for (int i = 0; i < components.size(); i++) {
      RootModelComponentBase rootModelComponentBase = components.get(i);
      rootModelComponentBase.projectOpened();
    }
  }

  public void projectClosed() {
    final ArrayList<RootModelComponentBase> components = myRootModel.myComponents;
    for (int i = 0; i < components.size(); i++) {
      RootModelComponentBase rootModelComponentBase = components.get(i);
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
    for (int i = 0; i < components.size(); i++) {
      RootModelComponentBase rootModelComponentBase = components.get(i);
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
    for (int i = 0; i < rootModels.size(); i++) {
      final RootModelImpl rootModel = rootModels.get(i);
      final String name = rootModel.getModule().getName();
      LOG.assertTrue(!nameToModel.containsKey(name));
      nameToModel.put(name, rootModel);
    }
    final Module[] modules = moduleModel.getModules();
    for (int i = 0; i < modules.length; i++) {
      final Module module = modules[i];
      final String name = module.getName();
      if (!nameToModel.containsKey(name)) {
        final RootModelImpl rootModel = ((ModuleRootManagerImpl)ModuleRootManager.getInstance(module)).myRootModel;
        nameToModel.put(name, rootModel);
      }
    }
    final Collection<RootModelImpl> allRootModels = nameToModel.values();
    DFSTBuilder<RootModelImpl> builder = new DFSTBuilder<RootModelImpl>(new GraphGenerator<RootModelImpl>(new CachingSemiGraph<RootModelImpl>(new GraphGenerator.SemiGraph<RootModelImpl>() {
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
            for (int i = 0; i < names.length; i++) {
              String name = names[i];
              final RootModelImpl depRootModel = nameToModel.get(name);
              if (depRootModel != null) { // it is ok not to find one
                result.add(depRootModel);
              }
            }
            return result.iterator();
          }
        })));
    return builder;
  }


  public VirtualFile[] getJavadocPaths() {
    return myRootModel.getJavadocPaths();
  }

  public String[] getJavadocUrls() {
    return myRootModel.getJavadocUrls();
  }

  public void dropCaches() {
    myCachedFiles.clear();
  }

  static void checkCircularDependencies(ModifiableRootModel[] _rootModels, ModifiableModuleModel moduleModel)
    throws ModuleCircularDependencyException {
    List<RootModelImpl> rootModels = new ArrayList<RootModelImpl>();
    for (int i = 0; i < _rootModels.length; i++) {
      RootModelImpl rootModel = (RootModelImpl)_rootModels[i];
      if (rootModel.isChanged()) {
        rootModels.add((RootModelImpl)rootModel);
      }
    }
    DFSTBuilder<RootModelImpl> dfstBuilder = createDFSTBuilder(rootModels, moduleModel);
    Pair<RootModelImpl, RootModelImpl> circularDependency = dfstBuilder.getCircularDependency();
    if (circularDependency != null) {
      throw new ModuleCircularDependencyException(circularDependency.first.getModule().getName(),
                                                  circularDependency.second.getModule().getName());
    }
  }
}
