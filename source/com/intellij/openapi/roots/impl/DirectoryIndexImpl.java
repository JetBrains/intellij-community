package com.intellij.openapi.roots.impl;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.impl.PsiManagerConfiguration;
import com.intellij.util.EventDispatcher;
import junit.framework.Assert;

import java.util.*;

public class DirectoryIndexImpl extends DirectoryIndex implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.DirectoryIndexImpl");

  private boolean LAZY_MODE;

  private final Project myProject;

  private boolean myInitialized = false;
  private boolean myDisposed = false;

  private com.intellij.util.containers.HashMap<VirtualFile, DirectoryInfo> myDirToInfoMap = new com.intellij.util.containers.HashMap<VirtualFile, DirectoryInfo>();
  private com.intellij.util.containers.HashMap<String, VirtualFile[]> myPackageNameToDirsMap = new com.intellij.util.containers.HashMap<String, VirtualFile[]>();

  private VirtualFileListener myVirtualFileListener;
  private FileTypeListener myFileTypeListener;
  private ModuleRootListener myRootListener;

  private PsiNameHelper myNameHelper;

  public DirectoryIndexImpl(Project project, PsiManagerConfiguration psiManagerConfiguration, StartupManagerEx startupManagerEx) {
    myProject = project;

    PsiManagerConfiguration configuration = psiManagerConfiguration;
    LAZY_MODE = !configuration.REPOSITORY_ENABLED;
    startupManagerEx.registerPreStartupActivity(
      new Runnable() {
        public void run() {
          initialize();
        }
      }
    );
  }

  public String getComponentName() {
    return "DirectoryIndex";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    if (myInitialized) {
      VirtualFileManager.getInstance().removeVirtualFileListener(myVirtualFileListener);
      FileTypeManager.getInstance().removeFileTypeListener(myFileTypeListener);
      ProjectRootManager.getInstance(myProject).removeModuleRootListener(myRootListener);
    }
    myDisposed = true;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  // for tests
  public void checkConsistency() {
    checkConsistency(false);
    checkConsistency(true);
  }

  private void checkConsistency(boolean reverseAllSets) {
    Assert.assertTrue(myInitialized);
    Assert.assertTrue(!myDisposed);

    com.intellij.util.containers.HashMap<VirtualFile, DirectoryInfo> oldDirToInfoMap = myDirToInfoMap;
    myDirToInfoMap = new com.intellij.util.containers.HashMap<VirtualFile, DirectoryInfo>();

    com.intellij.util.containers.HashMap<String, VirtualFile[]> oldPackageNameToDirsMap = myPackageNameToDirsMap;
    myPackageNameToDirsMap = new com.intellij.util.containers.HashMap<String, VirtualFile[]>();

    _initialize(reverseAllSets, null);

    if (LAZY_MODE) {
      com.intellij.util.containers.HashMap<VirtualFile, DirectoryInfo> newDirToInfoMap = myDirToInfoMap;
      com.intellij.util.containers.HashMap<String, VirtualFile[]> newPackageNameToDirsMap = myPackageNameToDirsMap;
      myDirToInfoMap = oldDirToInfoMap;
      myPackageNameToDirsMap = oldPackageNameToDirsMap;

      Set<VirtualFile> allDirsSet = newDirToInfoMap.keySet();
      for (Iterator<VirtualFile> iterator = allDirsSet.iterator(); iterator.hasNext();) {
        VirtualFile dir = iterator.next();
        getInfoForDirectory(dir);
      }

      myDirToInfoMap = newDirToInfoMap;
      myPackageNameToDirsMap = newPackageNameToDirsMap;
    }

    Set<VirtualFile> keySet = myDirToInfoMap.keySet();
    Assert.assertEquals(keySet, oldDirToInfoMap.keySet());
    for (Iterator<VirtualFile> iterator = keySet.iterator(); iterator.hasNext();) {
      VirtualFile file = iterator.next();
      DirectoryInfo info1 = myDirToInfoMap.get(file);
      DirectoryInfo info2 = oldDirToInfoMap.get(file);
      Assert.assertEquals(info1, info2);
    }

    Assert.assertEquals(myPackageNameToDirsMap.keySet(), oldPackageNameToDirsMap.keySet());
    for (Iterator<Map.Entry<String, VirtualFile[]>> iterator = myPackageNameToDirsMap.entrySet().iterator();
         iterator.hasNext();
      ) {
      Map.Entry<String, VirtualFile[]> entry = iterator.next();
      String packageName = entry.getKey();
      VirtualFile[] dirs = entry.getValue();
      VirtualFile[] dirs1 = oldPackageNameToDirsMap.get(packageName);

      HashSet<VirtualFile> set1 = new HashSet<VirtualFile>();
      set1.addAll(Arrays.asList(dirs));
      HashSet<VirtualFile> set2 = new HashSet<VirtualFile>();
      set2.addAll(Arrays.asList(dirs1));
      Assert.assertEquals(set1, set2);
    }
  }

  /**
   * @fabrique Used in fabrique
   */
  public void initialize() {
    myNameHelper = PsiManager.getInstance(myProject).getNameHelper();

    LOG.assertTrue(!myInitialized);
    LOG.assertTrue(!myDisposed);
    myInitialized = true;

    myVirtualFileListener = new MyVirtualFileListener();
    VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener);

    myFileTypeListener = new FileTypeListener() {
      public void beforeFileTypesChanged(FileTypeEvent event) {
      }

      public void fileTypesChanged(FileTypeEvent event) {
        _initialize();
      }
    };
    FileTypeManager.getInstance().addFileTypeListener(myFileTypeListener);

    myRootListener = new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        _initialize();
      }
    };
    ProjectRootManager.getInstance(myProject).addModuleRootListener(myRootListener);

    _initialize();
  }

  private void _initialize() {
    if (LAZY_MODE) {
      myDirToInfoMap.clear();
      myPackageNameToDirsMap.clear();
    }
    else {
      _initialize(false, null);
    }
  }

  private void _initialize(
    boolean reverseAllSets/* for testing order independence*/,
    VirtualFile forDir/* in LAZY_MODE only*/
    ) {

    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      progress.pushState();
      progress.setText("Scanning files...");
    }

    if (forDir == null) {
      myDirToInfoMap.clear();
      myPackageNameToDirsMap.clear();
    }

    if (forDir != null) {
      // clear map for all ancestors to not interfer with previous results
      VirtualFile dir = forDir;
      do {
        myDirToInfoMap.remove(dir);
        dir = dir.getParent();
      }
      while (dir != null);
    }

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    Module[] modules = moduleManager.getModules();
    if (reverseAllSets) {
      modules = (Module[])reverseArray(modules);
    }

    // exclude roots should be merged to prevent including excluded dirs of an inner module into the outer
    // exclude root should exclude from its content root and all outer content roots

    if (progress != null) {
      progress.setText2("Building exclude roots...");
    }
    Map<VirtualFile, Set<VirtualFile>> excludeRootsMap = new HashMap<VirtualFile, Set<VirtualFile>>();
    for (int i = 0; i < modules.length; i++) {
      ModuleRootManager rootManager = ModuleRootManager.getInstance(modules[i]);
      ContentEntry[] contentEntries = rootManager.getContentEntries();
      for (int j = 0; j < contentEntries.length; j++) {
        ContentEntry contentEntry = contentEntries[j];
        VirtualFile contentRoot = contentEntry.getFile();
        if (contentRoot == null) continue;

        VirtualFile[] excludeRoots = contentEntry.getExcludeFolderFiles();
        for (int k = 0; k < excludeRoots.length; k++) {
          putForFileAndAllAncestors(excludeRootsMap, contentRoot, excludeRoots[k]);
        }
      }
    }

    // fill module content's
    for (int i = 0; i < modules.length; i++) {
      Module module = modules[i];
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

      if (progress != null) {
        progress.setText2("Processing module \"" + module.getName() + "\" content...");
      }
      VirtualFile[] contentRoots = rootManager.getContentRoots();
      if (reverseAllSets) {
        contentRoots = (VirtualFile[])reverseArray(contentRoots);
      }

      for (int j = 0; j < contentRoots.length; j++) {
        final VirtualFile contentRoot = contentRoots[j];
        Set<VirtualFile> excludeRootsSet = excludeRootsMap.get(contentRoot);
        fillMapWithModuleContent(contentRoot, module, contentRoot, excludeRootsSet, forDir);
      }
    }

    // fill module sources
    for (int i = 0; i < modules.length; i++) {
      Module module = modules[i];
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

      if (progress != null) {
        progress.setText2("Processing module \"" + module.getName() + "\" sources...");
      }

      ContentEntry[] contentEntries = rootManager.getContentEntries();
      if (reverseAllSets) {
        contentEntries = (ContentEntry[])reverseArray(contentEntries);
      }
      for (int j = 0; j < contentEntries.length; j++) {
        ContentEntry contentEntry = contentEntries[j];
        SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
        if (reverseAllSets) {
          sourceFolders = (SourceFolder[])reverseArray(sourceFolders);
        }
        for (int k = 0; k < sourceFolders.length; k++) {
          SourceFolder sourceFolder = sourceFolders[k];
          VirtualFile dir = sourceFolder.getFile();
          if (dir != null) {
            fillMapWithModuleSource(dir, module, sourceFolder.getPackagePrefix(), dir, sourceFolder.isTestSource(), forDir);
          }
        }
      }
    }

    // fill library sources
    for (int i = 0; i < modules.length; i++) {
      Module module = modules[i];
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

      if (progress != null) {
        progress.setText2("Processing module \"" + module.getName() + "\" library sources...");
      }
      OrderEntry[] orderEntries = rootManager.getOrderEntries();
      for (int j = 0; j < orderEntries.length; j++) {
        OrderEntry orderEntry = orderEntries[j];

        boolean isLibrary = orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry;
        if (isLibrary) {
          VirtualFile[] sourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
          for (int k = 0; k < sourceRoots.length; k++) {
            final VirtualFile sourceRoot = sourceRoots[k];
            fillMapWithLibrarySources(sourceRoot, "", sourceRoot, forDir);
          }
        }
      }
    }

    // fill library classes
    for (int i = 0; i < modules.length; i++) {
      Module module = modules[i];
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

      if (progress != null) {
        progress.setText2("Processing module \"" + module.getName() + "\" library classes...");
      }
      OrderEntry[] orderEntries = rootManager.getOrderEntries();
      for (int j = 0; j < orderEntries.length; j++) {
        OrderEntry orderEntry = orderEntries[j];

        boolean isLibrary = orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry;
        if (isLibrary) {
          VirtualFile[] classRoots = orderEntry.getFiles(OrderRootType.CLASSES);
          for (int k = 0; k < classRoots.length; k++) {
            final VirtualFile classRoot = classRoots[k];
            fillMapWithLibraryClasses(classRoot, "", classRoot, forDir);
          }
        }
      }
    }

    if (progress != null) {
      progress.setText2("");
    }
    // fill order entries
    for (int i = 0; i < modules.length; i++) {
      Module module = modules[i];
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

      OrderEntry[] orderEntries = rootManager.getOrderEntries();
      for (int j = 0; j < orderEntries.length; j++) {
        OrderEntry orderEntry = orderEntries[j];
        List<OrderEntry> oneEntryList = Arrays.asList(new OrderEntry[]{orderEntry});


        if (orderEntry instanceof ModuleOrderEntry) {
          // [dsl] this is probably incorrect. However I do not see any other way (yet)
          // to make exporting work.
          Module entryModule = null;

          VirtualFile[] importedClassRoots = orderEntry.getFiles(OrderRootType.COMPILATION_CLASSES);
          for (int k = 0; k < importedClassRoots.length; k++) {
            fillMapWithOrderEntries(importedClassRoots[k], oneEntryList, entryModule, null, null, forDir);
          }

          VirtualFile[] sourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
          for (int k = 0; k < sourceRoots.length; k++) {
            fillMapWithOrderEntries(sourceRoots[k], oneEntryList, entryModule, null, null, forDir);
          }
        }
        else if (orderEntry instanceof ModuleSourceOrderEntry) {
          Module entryModule = orderEntry.getOwnerModule();

          VirtualFile[] sourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
          for (int k = 0; k < sourceRoots.length; k++) {
            fillMapWithOrderEntries(sourceRoots[k], oneEntryList, entryModule, null, null, forDir);
          }
        }
        else if (orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry){
          VirtualFile[] classRoots = orderEntry.getFiles(OrderRootType.CLASSES);
          for (int k = 0; k < classRoots.length; k++) {
            VirtualFile classRoot = classRoots[k];
            fillMapWithOrderEntries(classRoot, oneEntryList, null, classRoot, null, forDir);
          }

          VirtualFile[] sourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
          for (int k = 0; k < sourceRoots.length; k++) {
            VirtualFile sourceRoot = sourceRoots[k];
            fillMapWithOrderEntries(sourceRoot, oneEntryList, null, null, sourceRoot, forDir);
          }
        }
      }
    }

    if (progress != null) {
      progress.popState();
    }
  }

  private void putForFileAndAllAncestors(Map<VirtualFile, Set<VirtualFile>> map, VirtualFile file, VirtualFile value) {
    while (true) {
      Set<VirtualFile> set = map.get(file);
      if (set == null) {
        set = new HashSet<VirtualFile>();
        map.put(file, set);
      }
      set.add(value);

      file = file.getParent();
      if (file == null) break;
    }
  }

  // could not make it generic because of bug in compiler :-(
  private static Object[] reverseArray(Object[] array) {
    Object[] newArray = (Object[])array.clone();
    for (int i = 0; i < array.length; i++) {
      Object e = array[i];
      newArray[array.length - i - 1] = e;
    }
    return newArray;
  }

  public DirectoryInfo getInfoForDirectory(VirtualFile dir) {
    LOG.assertTrue(myInitialized);
    LOG.assertTrue(!myDisposed);

    dispatchPendingEvents();

    if (LAZY_MODE) {
      DirectoryInfo info = myDirToInfoMap.get(dir);
      if (info != null) return info;
      _initialize(false, dir);
    }

    return myDirToInfoMap.get(dir);
  }

  public VirtualFile[] getDirectoriesByPackageName(String packageName, boolean includeLibrarySources) {
    LOG.assertTrue(myInitialized);
    LOG.assertTrue(!myDisposed);

    VirtualFile[] allDirs = getDirectoriesByPackageName(packageName);
    if (includeLibrarySources) {
      return allDirs;
    }
    else {
      List<VirtualFile> result = new ArrayList<VirtualFile>();
      for (int i = 0; i < allDirs.length; i++) {
        VirtualFile dir = allDirs[i];
        DirectoryInfo info = getInfoForDirectory(dir);
        LOG.assertTrue(info != null);
        if (!info.isInLibrarySource || info.libraryClassRoot != null) {
          result.add(dir);
        }
      }
      return result.toArray(new VirtualFile[result.size()]);
    }
  }

  private VirtualFile[] getDirectoriesByPackageName(String packageName) {
    dispatchPendingEvents();

    if (!LAZY_MODE) {
      VirtualFile[] dirs = myPackageNameToDirsMap.get(packageName);
      return dirs != null ? dirs : VirtualFile.EMPTY_ARRAY;
    }
    else {
      VirtualFile[] dirs = myPackageNameToDirsMap.get(packageName);
      if (dirs != null) return dirs;
      dirs = _getDirectoriesByPackageNameInLazyMode(packageName);
      myPackageNameToDirsMap.put(packageName, dirs);
      return dirs;
    }
  }

  private VirtualFile[] _getDirectoriesByPackageNameInLazyMode(String packageName) {
    ArrayList<VirtualFile> list = new ArrayList<VirtualFile>();

    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (int i = 0; i < modules.length; i++) {
      Module module = modules[i];
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

      ContentEntry[] contentEntries = rootManager.getContentEntries();
      for (int j = 0; j < contentEntries.length; j++) {
        ContentEntry contentEntry = contentEntries[j];
        SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
        for (int k = 0; k < sourceFolders.length; k++) {
          SourceFolder sourceFolder = sourceFolders[k];
          VirtualFile sourceRoot = sourceFolder.getFile();
          if (sourceRoot != null) {
            findAndAddDirByPackageName(list, sourceRoot, packageName);
          }
        }
      }

      OrderEntry[] orderEntries = rootManager.getOrderEntries();
      for (int j = 0; j < orderEntries.length; j++) {
        OrderEntry orderEntry = orderEntries[j];
        if (orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry) {
          VirtualFile[] libRoots = orderEntry.getFiles(OrderRootType.CLASSES);
          for (int k = 0; k < libRoots.length; k++) {
            findAndAddDirByPackageName(list, libRoots[k], packageName);
          }

          VirtualFile[] libSourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
          for (int k = 0; k < libSourceRoots.length; k++) {
            findAndAddDirByPackageName(list, libSourceRoots[k], packageName);
          }
        }
      }
    }

    VirtualFile[] result = list.toArray(new VirtualFile[list.size()]);
    return result;
  }

  private void dispatchPendingEvents() {
    if (EventDispatcher.isDispatchingAnyEvent()){ // optimization
      VirtualFileManager.getInstance().dispatchPendingEvent(myVirtualFileListener);
      ProjectRootManager.getInstance(myProject).dispatchPendingEvent(myRootListener);
      //TODO: other listners!!!
    }
  }

  private void findAndAddDirByPackageName(ArrayList<VirtualFile> list, VirtualFile root, String packageName) {
    VirtualFile dir = findDirByPackageName(root, packageName);
    if (dir == null) return;
    DirectoryInfo info = getInfoForDirectory(dir);
    if (info == null) return;
    if (!packageName.equals(info.packageName)) return;
    if (!list.contains(dir)) {
      list.add(dir);
    }
  }

  private static VirtualFile findDirByPackageName(VirtualFile root, String packageName) {
    if (packageName.length() == 0) {
      return root;
    }
    else {
      int index = packageName.indexOf('.');
      if (index < 0) {
        VirtualFile child = root.findChild(packageName);
        if (child == null || !child.isDirectory()) return null;
        return child;
      }
      else {
        String name = packageName.substring(0, index);
        String restName = packageName.substring(index + 1);
        VirtualFile child = root.findChild(name);
        if (child == null || !child.isDirectory()) return null;
        return findDirByPackageName(child, restName);
      }
    }
  }

  private void fillMapWithModuleContent(VirtualFile dir,
                                      Module module,
                                      VirtualFile contentRoot,
                                      Set<VirtualFile> excludeRoots,
                                      VirtualFile forDir) {
    if (excludeRoots != null && excludeRoots.contains(dir)) return;
    if (FileTypeManager.getInstance().isFileIgnored(dir.getName())) return;

    if (forDir != null) {
      if (!VfsUtil.isAncestor(dir, forDir, false)) return;
    }

    DirectoryInfo info = getOrCreateDirInfo(dir);

    if (info.module != null) { // module contents overlap
      DirectoryInfo parentInfo = myDirToInfoMap.get(dir.getParent());
      if (parentInfo == null || !info.module.equals(parentInfo.module)) return; // content of another module is below this module's content
    }

    VirtualFile[] children = dir.getChildren();
    for (int i = 0; i < children.length; i++) {
      VirtualFile child = children[i];
      if (child.isDirectory()) {
        fillMapWithModuleContent(child, module, contentRoot, excludeRoots, forDir);
      }
    }

    // important to change module AFTER processing children - to handle overlapping modules
    info.module = module;
    info.contentRoot = contentRoot;
  }

  private void fillMapWithModuleSource(VirtualFile dir,
                                     Module module,
                                     String packageName,
                                     VirtualFile sourceRoot,
                                     boolean isTestSource,
                                     VirtualFile forDir) {
    DirectoryInfo info = myDirToInfoMap.get(dir);
    if (info == null) return;
    if (!module.equals(info.module)) return;

    if (forDir != null) {
      if (!VfsUtil.isAncestor(dir, forDir, false)) return;
    }

    if (info.isInModuleSource) { // module sources overlap
      if (info.packageName != null && info.packageName.length() == 0) return; // another source root starts here
    }

    info.isInModuleSource = true;
    info.isTestSource = isTestSource;
    info.sourceRoot = sourceRoot;
    setPackageName(dir, info, packageName);

    VirtualFile[] children = dir.getChildren();
    for (int i = 0; i < children.length; i++) {
      VirtualFile child = children[i];
      if (child.isDirectory()) {
        String childPackageName = getPackageNameForSubdir(packageName, child.getName());
        fillMapWithModuleSource(child, module, childPackageName, sourceRoot, isTestSource, forDir);
      }
    }
  }

  private void fillMapWithLibraryClasses(VirtualFile dir,
                                         String packageName,
                                         VirtualFile classRoot,
                                         VirtualFile forDir) {
    if (FileTypeManager.getInstance().isFileIgnored(dir.getName())) return;

    if (forDir != null) {
      if (!VfsUtil.isAncestor(dir, forDir, false)) return;
    }

    DirectoryInfo info = getOrCreateDirInfo(dir);

    if (info.libraryClassRoot != null) { // library classes overlap
      if (info.packageName != null && info.packageName.length() == 0) return; // another library root starts here
    }

    info.libraryClassRoot = classRoot;

    if (!info.isInModuleSource && !info.isInLibrarySource) {
      setPackageName(dir, info, packageName);
    }

    VirtualFile[] children = dir.getChildren();
    for (int i = 0; i < children.length; i++) {
      VirtualFile child = children[i];
      if (child.isDirectory()) {
        String childPackageName = getPackageNameForSubdir(packageName, child.getName());
        fillMapWithLibraryClasses(child, childPackageName, classRoot, forDir);
      }
    }
  }

  private void fillMapWithLibrarySources(VirtualFile dir,
                                         String packageName,
                                         VirtualFile sourceRoot,
                                         VirtualFile forDir) {
    if (FileTypeManager.getInstance().isFileIgnored(dir.getName())) return;

    if (forDir != null) {
      if (!VfsUtil.isAncestor(dir, forDir, false)) return;
    }

    DirectoryInfo info = getOrCreateDirInfo(dir);

    if (info.isInLibrarySource) { // library sources overlap
      if (info.packageName != null && info.packageName.length() == 0) return; // another library source root starts here
    }

    info.isInModuleSource = false;
    info.isInLibrarySource = true;
    info.sourceRoot = sourceRoot;
    setPackageName(dir, info, packageName);

    VirtualFile[] children = dir.getChildren();
    for (int i = 0; i < children.length; i++) {
      VirtualFile child = children[i];
      if (child.isDirectory()) {
        String childPackageName = getPackageNameForSubdir(packageName, child.getName());
        fillMapWithLibrarySources(child, childPackageName, sourceRoot, forDir);
      }
    }
  }

  private void fillMapWithOrderEntries(VirtualFile dir,
                                       Collection<OrderEntry> orderEntries,
                                       Module module,
                                       VirtualFile libraryClassRoot,
                                       VirtualFile librarySourceRoot,
                                       VirtualFile forDir) {
    if (FileTypeManager.getInstance().isFileIgnored(dir.getName())) return;

    if (forDir != null) {
      if (!VfsUtil.isAncestor(dir, forDir, false)) return;
    }

    DirectoryInfo info = myDirToInfoMap.get(dir); // do not create it here!
    if (info == null) return;

    if (module != null) {
      if (info.module != module) return;
      if (!info.isInModuleSource) return;
    }
    else if (libraryClassRoot != null){
      if (info.libraryClassRoot != libraryClassRoot) return;
      if (info.isInModuleSource) return;
    }
    else if (librarySourceRoot != null){
      if (!info.isInLibrarySource) return;
      if (info.sourceRoot != librarySourceRoot) return;
      if (info.libraryClassRoot != null) return;
    }

    info.orderEntries.addAll(orderEntries);

    final VirtualFile[] children = dir.getChildren();
    for (int i = 0; i < children.length; i++) {
      VirtualFile child = children[i];
      if (child.isDirectory()) {
        fillMapWithOrderEntries(child, orderEntries, module, libraryClassRoot, librarySourceRoot, forDir);
      }
    }
  }

  private DirectoryInfo getOrCreateDirInfo(VirtualFile dir) {
    DirectoryInfo info = myDirToInfoMap.get(dir);
    if (info == null) {
      info = new DirectoryInfo();
      myDirToInfoMap.put(dir, info);
    }
    return info;
  }

  private void setPackageName(VirtualFile dir, DirectoryInfo info, String newPackageName) {
    LOG.assertTrue(dir != null);
    if (!LAZY_MODE) {
      String oldPackageName = info.packageName;
      if (oldPackageName != null) {
        VirtualFile[] oldPackageDirs = myPackageNameToDirsMap.get(oldPackageName);
        LOG.assertTrue(oldPackageDirs != null);
        LOG.assertTrue(oldPackageDirs.length > 0);
        VirtualFile[] dirs;
        if (oldPackageDirs.length != 1) {
          dirs = new VirtualFile[oldPackageDirs.length - 1];

          boolean found = false;
          for (int i = 0; i < oldPackageDirs.length; i++) {
            VirtualFile oldDir = oldPackageDirs[i];
            if (oldDir.equals(dir)) {
              found = true;
              continue;
            }
            dirs[found ? i - 1 : i] = oldDir;
          }
          LOG.assertTrue(found);

          myPackageNameToDirsMap.put(oldPackageName, dirs);
        }
        else {
          LOG.assertTrue(dir.equals(oldPackageDirs[0]));
          myPackageNameToDirsMap.remove(oldPackageName);
        }

      }

      if (newPackageName != null) {
        VirtualFile[] newPackageDirs = myPackageNameToDirsMap.get(newPackageName);
        VirtualFile[] dirs;
        if (newPackageDirs == null) {
          dirs = new VirtualFile[]{dir};
        }
        else {
          dirs = new VirtualFile[newPackageDirs.length + 1];
          System.arraycopy(newPackageDirs, 0, dirs, 0, newPackageDirs.length);
          dirs[newPackageDirs.length] = dir;
        }
        myPackageNameToDirsMap.put(newPackageName, dirs);
      }
    }
    else {
      if (info.packageName != null) {
        myPackageNameToDirsMap.remove(info.packageName);
      }
      if (newPackageName != null) {
        myPackageNameToDirsMap.remove(newPackageName);
      }
    }

    info.packageName = newPackageName;
  }

  private String getPackageNameForSubdir(String parentPackageName, String subdirName) {
    if (parentPackageName == null) return null;
    if (!myNameHelper.isIdentifier(subdirName)) return null;
    return parentPackageName.length() > 0 ? parentPackageName + "." + subdirName : subdirName;
  }

  private class MyVirtualFileListener implements VirtualFileListener {
    private final Key FILES_TO_RELEASE_KEY = Key.create("DirectoryIndexImpl.MyVirtualFileListener.FILES_TO_RELEASE_KEY");

    public void fileCreated(VirtualFileEvent event) {
      VirtualFile file = event.getFile();
      if (!file.isDirectory()) return;
      VirtualFile parent = file.getParent();
      if (parent == null) return;
      if (FileTypeManager.getInstance().isFileIgnored(file.getName())) return;

      DirectoryInfo parentInfo = myDirToInfoMap.get(parent);
      if (parentInfo == null) return;

      if (!LAZY_MODE) {
        if (parentInfo.module != null) {
          fillMapWithModuleContent(file, parentInfo.module, parentInfo.contentRoot, null, null);

          if (parentInfo.isInModuleSource) {
            String newDirPackageName = getPackageNameForSubdir(parentInfo.packageName, file.getName());
            fillMapWithModuleSource(file, parentInfo.module, newDirPackageName, parentInfo.sourceRoot,
                                    parentInfo.isTestSource, null);
          }
        }

        if (parentInfo.libraryClassRoot != null) {
          String newDirPackageName = getPackageNameForSubdir(parentInfo.packageName, file.getName());
          fillMapWithLibraryClasses(file, newDirPackageName, parentInfo.libraryClassRoot, null);
        }

        if (parentInfo.isInLibrarySource) {
          String newDirPackageName = getPackageNameForSubdir(parentInfo.packageName, file.getName());
          fillMapWithLibrarySources(file, newDirPackageName, parentInfo.sourceRoot, null);
        }

        if (parentInfo.orderEntries.size() > 0) {
          fillMapWithOrderEntries(file, parentInfo.orderEntries, null, null, null, null);
        }
      }
    }

    public void beforeFileDeletion(VirtualFileEvent event) {
      VirtualFile file = event.getFile();
      if (!file.isDirectory()) return;
      if (!myDirToInfoMap.containsKey(file)) return;

      ArrayList<VirtualFile> list = new ArrayList<VirtualFile>();
      addDirsRecursively(list, file);
      file.putUserData(FILES_TO_RELEASE_KEY, list);
    }

    private void addDirsRecursively(ArrayList<VirtualFile> list, VirtualFile dir) {
      if (!myDirToInfoMap.containsKey(dir)) return;

      list.add(dir);

      VirtualFile[] children = dir.getChildren();
      for (int i = 0; i < children.length; i++) {
        VirtualFile child = children[i];
        if (child.isDirectory()) {
          addDirsRecursively(list, child);
        }
      }
    }

    public void fileDeleted(VirtualFileEvent event) {
      VirtualFile file = event.getFile();
      ArrayList<VirtualFile> list = (ArrayList<VirtualFile>)file.getUserData(FILES_TO_RELEASE_KEY);
      if (list == null) return;

      for (int i = 0; i < list.size(); i++) {
        VirtualFile dir = list.get(i);
        DirectoryInfo info = myDirToInfoMap.remove(dir);
        if (info != null) {
          setPackageName(dir, info, null);
        }
      }
    }

    public void beforeFileMovement(VirtualFileMoveEvent event) {
    }

    public void fileMoved(VirtualFileMoveEvent event) {
      VirtualFile file = event.getFile();
      if (file.isDirectory()) {
        _initialize();
      }
    }

    public void beforePropertyChange(VirtualFilePropertyEvent event) {
    }

    public void propertyChanged(VirtualFilePropertyEvent event) {
      if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
        VirtualFile file = event.getFile();
        if (file.isDirectory()) {
          _initialize();
        }
      }
    }

    public void contentsChanged(VirtualFileEvent event) {
    }

    public void beforeContentsChange(VirtualFileEvent event) {
    }
  }
}
