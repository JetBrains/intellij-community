package com.intellij.packageDependencies.ui;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.scopeView.nodes.ClassNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.Icons;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TreeModelBuilder {
  private static final Key<Integer> FILE_COUNT = Key.create("FILE_COUNT");
  private ProjectFileIndex myFileIndex;
  private PsiManager myPsiManager;
  private Project myProject;
  private static final Logger LOG = Logger.getInstance("com.intellij.packageDependencies.ui.TreeModelBuilder");
  private boolean myShowModuleGroups;

  private static enum ScopeType {
    TEST, SOURCE, LIB
  }

  private boolean myShowModules;
  private boolean myGroupByScopeType;
  private boolean myFlattenPackages;
  private boolean myCompactEmptyMiddlePackages;
  private boolean myShowFiles;
  private boolean myShowIndividualLibs;
  private Marker myMarker;
  private boolean myAddUnmarkedFiles;
  private PackageDependenciesNode myRoot;
  private Map<ScopeType, Map<PsiDirectory,DirectoryNode>> myModuleDirNodes = new HashMap<ScopeType, Map<PsiDirectory, DirectoryNode>>();
  private Map<ScopeType, Map<Pair<Module, PsiPackage>, PackageNode>> myModulePackageNodes = new HashMap<ScopeType, Map<Pair<Module, PsiPackage>, PackageNode>>();
  private Map<ScopeType, Map<Pair<OrderEntry, PsiPackage>, PackageNode>> myLibraryPackageNodes = new HashMap<ScopeType, Map<Pair<OrderEntry, PsiPackage>, PackageNode>>();
  private Map<ScopeType, Map<Module, ModuleNode>> myModuleNodes = new HashMap<ScopeType, Map<Module, ModuleNode>>();
  private Map<ScopeType, Map<String, ModuleGroupNode>> myModuleGroupNodes = new HashMap<ScopeType, Map<String, ModuleGroupNode>>();
  private Map<ScopeType, Map<OrderEntry, LibraryNode>> myLibraryNodes = new HashMap<ScopeType, Map<OrderEntry, LibraryNode>>();
  private int myScannedFileCount = 0;
  private int myTotalFileCount = 0;
  private int myMarkedFileCount = 0;
  private GeneralGroupNode myAllLibsNode = null;

  private GeneralGroupNode mySourceRoot = null;
  private GeneralGroupNode myTestRoot = null;
  private GeneralGroupNode myLibsRoot = null;


  private boolean myGroupByFiles;

  private static final Icon LIB_ICON_OPEN = IconLoader.getIcon("/nodes/ppLibOpen.png");
  private static final Icon LIB_ICON_CLOSED = IconLoader.getIcon("/nodes/ppLibClosed.png");
  private static final Icon TEST_ICON = IconLoader.getIcon("/nodes/testSourceFolder.png");
  public static final String PRODUCTION_NAME = AnalysisScopeBundle.message("package.dependencies.production.node.text");
  public static final String TEST_NAME = AnalysisScopeBundle.message("package.dependencies.test.node.text");
  public static final String LIBRARY_NAME = AnalysisScopeBundle.message("package.dependencies.library.node.text");

  public TreeModelBuilder(Project project, boolean showIndividualLibs, Marker marker, DependenciesPanel.DependencyPanelSettings settings) {
    myProject = project;
    myShowModules = settings.UI_SHOW_MODULES;
    myGroupByScopeType = settings.UI_GROUP_BY_SCOPE_TYPE;
    myFlattenPackages = settings.UI_FLATTEN_PACKAGES;
    myCompactEmptyMiddlePackages = settings.UI_COMPACT_EMPTY_MIDDLE_PACKAGES;
    myShowFiles = settings.UI_SHOW_FILES;
    myGroupByFiles = settings.UI_GROUP_BY_FILES;
    myShowIndividualLibs = showIndividualLibs;
    myShowModuleGroups = settings.UI_SHOW_MODULE_GROUPS;
    myMarker = marker;
    myAddUnmarkedFiles = !settings.UI_FILTER_LEGALS;
    myRoot = new RootNode();
    myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myPsiManager = PsiManager.getInstance(project);

    createMaps(ScopeType.LIB);
    createMaps(ScopeType.SOURCE);
    createMaps(ScopeType.TEST);

    if (myGroupByScopeType) {
      mySourceRoot = new GeneralGroupNode(PRODUCTION_NAME, Icons.PACKAGE_OPEN_ICON, Icons.PACKAGE_ICON);
      myTestRoot = new GeneralGroupNode(TEST_NAME, TEST_ICON, TEST_ICON);
      myLibsRoot = new GeneralGroupNode(LIBRARY_NAME, LIB_ICON_OPEN, LIB_ICON_CLOSED);
      myRoot.add(mySourceRoot);
      myRoot.add(myTestRoot);
      myRoot.add(myLibsRoot);
    }
  }

  private void createMaps(ScopeType scopeType) {
    myModuleDirNodes.put(scopeType, new HashMap<PsiDirectory, DirectoryNode>());
    myModulePackageNodes.put(scopeType, new HashMap<Pair<Module, PsiPackage>, PackageNode>());
    myLibraryPackageNodes.put(scopeType, new THashMap<Pair<OrderEntry, PsiPackage>, PackageNode>(new TObjectHashingStrategy<Pair<OrderEntry, PsiPackage>>() {
      public int computeHashCode(final Pair<OrderEntry, PsiPackage> key) {
        return key.getSecond() == null ? 0 : key.getSecond().hashCode();
      }

      public boolean equals(final Pair<OrderEntry, PsiPackage> o1, final Pair<OrderEntry, PsiPackage> o2) {
        return Comparing.equal(o1.getSecond(), o2.getSecond());
      }
    }));
    myModuleGroupNodes.put(scopeType, new HashMap<String, ModuleGroupNode>());
    myModuleNodes.put(scopeType, new HashMap<Module, ModuleNode>());
    myLibraryNodes.put(scopeType, new HashMap<OrderEntry, LibraryNode>());
  }

  public static class TreeModel extends DefaultTreeModel {
    private final int myMarkedFileCount;
    private final int myTotalFileCount;

    public TreeModel(TreeNode root, int total, int marked) {
      super(root);
      myMarkedFileCount = marked;
      myTotalFileCount = total;
    }

    public int getMarkedFileCount() {
      return myMarkedFileCount;
    }

    public int getTotalFileCount() {
      return myTotalFileCount;
    }
  }

  public interface Marker {
    boolean isMarked(PsiFile file);
  }

  public static synchronized TreeModel createTreeModel(Project project, boolean showProgress, Set<PsiFile> files, Marker marker, DependenciesPanel.DependencyPanelSettings settings) {
    return new TreeModelBuilder(project, true, marker, settings).build(files, showProgress);
  }

  public static synchronized TreeModel createTreeModel(Project project, Marker marker, DependenciesPanel.DependencyPanelSettings settings) {
    return new TreeModelBuilder(project, true, marker, settings).build(project, false);
  }

  public static synchronized TreeModel createTreeModel(Project project,
                                                       boolean showProgress,
                                                       boolean showIndividualLibs,
                                                       Marker marker) {
    return new TreeModelBuilder(project, showIndividualLibs, marker, new DependenciesPanel.DependencyPanelSettings()).build(project, showProgress);
  }

  private void countFiles(Project project) {
    final Integer fileCount = project.getUserData(FILE_COUNT);
    if (fileCount == null) {
      myFileIndex.iterateContent(new ContentIterator() {
        public boolean processFile(VirtualFile fileOrDir) {
          if (!fileOrDir.isDirectory()) {
            counting(fileOrDir);
          }
          return true;
        }
      });

      if (!myGroupByFiles) {
        for (VirtualFile root : LibraryUtil.getLibraryRoots(project)) {
          countFilesRecursively(root);
        }
      }
      project.putUserData(FILE_COUNT, myTotalFileCount);
    } else {
      myTotalFileCount = fileCount.intValue();
    }
  }

  public static void clearCaches(Project project) {
    project.putUserData(FILE_COUNT, null);
  }

  public TreeModel build(final Project project, boolean showProgress) {
    return build(project, showProgress, false);
  }

  public TreeModel build(final Project project, final boolean showProgress, final boolean sortByType) {
    Runnable buildingRunnable = new Runnable() {
      public void run() {
        countFiles(project);
        final PsiManager psiManager = PsiManager.getInstance(project);
        myFileIndex.iterateContent(new ContentIterator() {
          public boolean processFile(VirtualFile fileOrDir) {
            if (!fileOrDir.isDirectory()) {
              final PsiFile psiFile = psiManager.findFile(fileOrDir);
              if (psiFile != null) {
                buildFileNode(psiFile);
              }
            }
            return true;
          }
        });

        if (!myGroupByFiles) {
          for (VirtualFile root : LibraryUtil.getLibraryRoots(project)) {
            processFilesRecursively(root, psiManager);
          }
        }
      }
    };

    if (showProgress) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(buildingRunnable, AnalysisScopeBundle.message("package.dependencies.build.process.title"), true, project);
    }
    else {
      buildingRunnable.run();
    }

    TreeUtil.sort(myRoot, new DependencyNodeComparator(sortByType));
    return new TreeModel(myRoot, myTotalFileCount, myMarkedFileCount);
  }

  private void processFilesRecursively(VirtualFile file, PsiManager psiManager) {
    if (file.isDirectory()) {
      VirtualFile[] children = file.getChildren();
      for (VirtualFile aChildren : children) {
        processFilesRecursively(aChildren, psiManager);
      }
    }
    else {
      final PsiFile psiFile = psiManager.findFile(file);
      if (psiFile != null) { // skip inners & anonymous
        buildFileNode(psiFile);
      }
    }
  }

  private void countFilesRecursively(VirtualFile file) {
    if (file.isDirectory()) {
      VirtualFile[] children = file.getChildren();
      for (VirtualFile aChildren : children) {
        countFilesRecursively(aChildren);
      }
    }
    else {
      counting(file);
    }
  }

  private void counting(final VirtualFile file) {
    myTotalFileCount++;
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText(AnalysisScopeBundle.message("package.dependencies.build.progress.text"));
      indicator.setIndeterminate(true);
      indicator.setText2(file.getPresentableUrl());
    }
  }

  private TreeModel build(final Set<PsiFile> files, boolean showProgress) {
    if (files.size() == 1) {
      myShowFiles = true;
    }

    Runnable buildingRunnable = new Runnable() {
      public void run() {
        for (final PsiFile file : files) {
          if (file != null) {
            buildFileNode(file);
          }
        }
      }
    };

    if (showProgress) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(buildingRunnable, AnalysisScopeBundle.message("package.dependencies.build.process.title"), false, myProject);
    }
    else {
      buildingRunnable.run();
    }

    TreeUtil.sort(myRoot, new DependencyNodeComparator());
    return new TreeModel(myRoot, myTotalFileCount, myMarkedFileCount);
  }

  private void buildFileNode(PsiFile file) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setIndeterminate(false);
      indicator.setText(AnalysisScopeBundle.message("package.dependencies.build.progress.text"));
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        indicator.setText2(virtualFile.getPresentableUrl());
      }
      indicator.setFraction(((double)myScannedFileCount++) / myTotalFileCount);
    }

    if (file == null || !file.isValid()) return;
    boolean isMarked = myMarker != null && myMarker.isMarked(file);
    if (isMarked) myMarkedFileCount++;
    if (isMarked || myAddUnmarkedFiles) {
      PackageDependenciesNode dirNode = getFileParentNode(file);

      if (myShowFiles) {
        FileNode fileNode = new FileNode(file, isMarked);
        dirNode.add(fileNode);
      }
      else {
        dirNode.addFile(file, isMarked);
      }
    }
  }

  public @NotNull PackageDependenciesNode getFileParentNode(PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    LOG.assertTrue(vFile != null);
    final VirtualFile containingDirectory = vFile.getParent();
    LOG.assertTrue(containingDirectory != null);
    if (!myGroupByFiles) {
      PsiPackage aPackage = null;
      if (file instanceof PsiJavaFile){
        aPackage = getFilePackage((PsiJavaFile)file);
      } else {
        final String packageName = myFileIndex.getPackageNameByDirectory(containingDirectory);
        if (packageName != null) {
          aPackage = myPsiManager.findPackage(packageName);
        }
      }
      if (aPackage != null) {
        if (myFileIndex.isInLibrarySource(vFile) || myFileIndex.isInLibraryClasses(vFile)) {
          return getLibraryDirNode(aPackage, getLibraryForFile(file));
        }
        else {
          return getModuleDirNode(aPackage, myFileIndex.getModuleForFile(vFile), getFileScopeType(vFile));
        }
      }
      return getModuleNode(myFileIndex.getModuleForFile(vFile), getFileScopeType(vFile));
    } else {
      return getModuleDirNode(file.getContainingDirectory(), myFileIndex.getModuleForFile(vFile), getFileScopeType(vFile), null);
    }
  }

  @Nullable
  public DefaultMutableTreeNode removeNode(final PsiElement element, PsiDirectory parent) {
    Module module = myFileIndex.getModuleForFile(parent.getVirtualFile());
    if (element instanceof PsiDirectory && myFlattenPackages) {
      final PackageDependenciesNode moduleNode = getModuleNode(module, ScopeType.SOURCE);
      final PsiDirectory psiDirectory = (PsiDirectory)element;
      final VirtualFile virtualFile = psiDirectory.getVirtualFile();
      final PackageDependenciesNode dirNode =
        getModuleDirNode(psiDirectory, myFileIndex.getModuleForFile(virtualFile), getFileScopeType(virtualFile), null);
      dirNode.removeFromParent();
      return moduleNode;
    }
    DefaultMutableTreeNode dirNode = getModuleDirNode(parent, module, ScopeType.SOURCE, null);
    if (dirNode == null) return null;
    final PackageDependenciesNode[] classOrDirNodes = findNodeForPsiElement((PackageDependenciesNode)dirNode, element);
    if (classOrDirNodes != null){
      for (PackageDependenciesNode classNode : classOrDirNodes) {
        classNode.removeFromParent();
      }
    }

    DefaultMutableTreeNode node = dirNode;
    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode)node.getParent();
    while (node != null && node.getChildCount() == 0) {
      PsiDirectory directory = parent.getParentDirectory();
      parentNode = (DefaultMutableTreeNode)node.getParent();
      node.removeFromParent();
      if (node instanceof DirectoryNode) {
        while (node != null) {  //clear all compacted links
          getMap(myModuleDirNodes, ScopeType.SOURCE).put((PsiDirectory)((DirectoryNode)node).getPsiElement(), null);
          node = ((DirectoryNode)node).getCompactedDirNode();
        }
      } else if (parentNode instanceof ModuleNode) {
        getMap(myModuleNodes, ScopeType.SOURCE).put(((ModuleNode)parentNode).getModule(), null);
      } else if (parentNode instanceof ModuleGroupNode) {
        getMap(myModuleGroupNodes, ScopeType.SOURCE).put(((ModuleGroupNode)parentNode).getModuleGroupName(), null);
      }
      node = parentNode;
      parent = directory;
    }
    if (myCompactEmptyMiddlePackages && node instanceof DirectoryNode && node.getChildCount() == 1) { //compact
      final TreeNode treeNode = node.getChildAt(0);
      if (treeNode instanceof DirectoryNode){
        node.removeAllChildren();
        for (int i = treeNode.getChildCount() - 1; i >= 0; i--){
          node.add((MutableTreeNode)treeNode.getChildAt(i));
        }
        ((DirectoryNode)node).setCompactedDirNode((DirectoryNode)treeNode);
      }
    }
    return parentNode;
  }

  @Nullable
  public PackageDependenciesNode addFileNode(final PsiFile file){
    boolean isMarked = myMarker != null && myMarker.isMarked(file);
    if (!isMarked) return null;

    final VirtualFile vFile = file.getVirtualFile();
    LOG.assertTrue(vFile != null);
    PsiDirectory dirToReload = file.getContainingDirectory();
    final ScopeType scopeType = getFileScopeType(vFile);
    PackageDependenciesNode rootToReload = getMap(myModuleDirNodes, scopeType).get(dirToReload);
    if (rootToReload == null && myFlattenPackages) {
      final Module module = myFileIndex.getModuleForFile(vFile);
      final boolean moduleNodeExist = getMap(myModuleNodes, scopeType).get(module) != null;
      rootToReload = getModuleNode(module, scopeType);
      if (!moduleNodeExist) {
        rootToReload = null; //need to reload from parent / mostly for problems view
      }
    } else {
      while (rootToReload == null && dirToReload != null){
        dirToReload = dirToReload.getParentDirectory();
        rootToReload = getMap(myModuleDirNodes, scopeType).get(dirToReload);
      }
    }

    PackageDependenciesNode dirNode = getFileParentNode(file);
    dirNode.add(new FileNode(file, isMarked));
    return rootToReload;
  }

  @Nullable
  public PackageDependenciesNode findNode(PsiFile file) {
    PackageDependenciesNode parent = getFileParentNode(file);
    PackageDependenciesNode[] nodes = findNodeForPsiElement(parent, file);
    return nodes == null || nodes.length == 0 ? null : nodes[0];
  }

  @Nullable
  private static PackageDependenciesNode[] findNodeForPsiElement(PackageDependenciesNode parent, PsiElement element){
    final Set<PackageDependenciesNode> result = new HashSet<PackageDependenciesNode>();
    for (int i = 0; i < parent.getChildCount(); i++){
      final TreeNode treeNode = parent.getChildAt(i);
      if (treeNode instanceof PackageDependenciesNode){
        final PackageDependenciesNode node = (PackageDependenciesNode)treeNode;
        if (element instanceof PsiDirectory && node.getPsiElement() == element){
          return new PackageDependenciesNode[] {node};
        }
        if (element instanceof PsiFile) {
          PsiFile psiFile = null;
          if (node instanceof ClassNode) {
            psiFile = ((ClassNode)node).getContainingFile();
          }
          else if (node instanceof FileNode) { //non java files
            psiFile = ((PsiFile)node.getPsiElement());
          }
          if (psiFile != null && psiFile.getVirtualFile() == ((PsiFile)element).getVirtualFile()) {
            result.add(node);
          }
        }
      }
    }
    return result.isEmpty() ? null : result.toArray(new PackageDependenciesNode[result.size()]);
  }

  @Nullable
  private PsiPackage getFilePackage(PsiJavaFile file) {
    VirtualFile vFile = file.getVirtualFile();
    if (vFile != null && myFileIndex.isInLibrarySource(vFile)) {
      final VirtualFile directory = vFile.getParent();
      if (directory != null) {
        final String packageName = myFileIndex.getPackageNameByDirectory(directory);
        if (packageName != null) {
          return myPsiManager.findPackage(packageName);
        }
      }
    }
    return myPsiManager.findPackage(file.getPackageName());
  }

  private ScopeType getFileScopeType(VirtualFile file) {
    if (myFileIndex.isLibraryClassFile(file) || myFileIndex.isInLibrarySource(file)) return ScopeType.LIB;
    if (myFileIndex.isInTestSourceContent(file)) return ScopeType.TEST;
    return ScopeType.SOURCE;
  }

  @Nullable
  private OrderEntry getLibraryForFile(PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;
    List<OrderEntry> orders = myFileIndex.getOrderEntriesForFile(virtualFile);
    for (OrderEntry order : orders) {
      if (order instanceof LibraryOrderEntry || order instanceof JdkOrderEntry) return order;
    }
    return null;
  }

  private <T> T getMap(Map<ScopeType, T> map, ScopeType scopeType) {
    return map.get(myGroupByScopeType ? scopeType : ScopeType.SOURCE);
  }

  private PackageDependenciesNode getLibraryDirNode(PsiPackage aPackage, OrderEntry libraryOrJdk) {
    if (aPackage == null || aPackage.getName() == null) {
      return getLibraryOrJDKNode(libraryOrJdk);
    }

    if (!myShowModules && !myGroupByScopeType) {
      return getModuleDirNode(aPackage, null, ScopeType.LIB);
    }

    Pair<OrderEntry, PsiPackage> descriptor = new Pair<OrderEntry, PsiPackage>(myShowModules ? libraryOrJdk : null, aPackage);
    PackageNode node = getMap(myLibraryPackageNodes, ScopeType.LIB).get(descriptor);
    if (node != null) return node;

    node = new PackageNode(aPackage, myFlattenPackages);
    getMap(myLibraryPackageNodes, ScopeType.LIB).put(descriptor, node);

    if (myFlattenPackages) {
      getLibraryOrJDKNode(libraryOrJdk).add(node);
    }
    else {
      getLibraryDirNode(aPackage.getParentPackage(), libraryOrJdk).add(node);
    }

    return node;
  }

  private PackageDependenciesNode getModuleDirNode(PsiPackage aPackage, Module module, ScopeType scopeType) {
    if (aPackage == null) {
      return getModuleNode(module, scopeType);
    }

    Pair<Module, PsiPackage> descriptor = new Pair<Module, PsiPackage>(myShowModules ? module : null, aPackage);
    PackageNode node = getMap(myModulePackageNodes, scopeType).get(descriptor);

    if (node != null) return node;

    node = new PackageNode(aPackage, myFlattenPackages);
    getMap(myModulePackageNodes, scopeType).put(descriptor, node);

    if (myFlattenPackages) {
      getModuleNode(module, scopeType).add(node);
    }
    else {
      getModuleDirNode(aPackage.getParentPackage(), module, scopeType).add(node);
    }

    return node;
  }

  private PackageDependenciesNode getModuleDirNode(PsiDirectory psiDirectory, Module module, ScopeType scopeType, DirectoryNode childNode) {
    if (psiDirectory == null) {
      return getModuleNode(module, scopeType);
    }

    PackageDependenciesNode directoryNode = getMap(myModuleDirNodes, scopeType).get(psiDirectory);
    if (directoryNode != null) {
      if (myCompactEmptyMiddlePackages) {
        DirectoryNode nestedNode = ((DirectoryNode)directoryNode).getCompactedDirNode();
        if (nestedNode != null) { //decompact
          DirectoryNode parentWrapper = nestedNode.getWrapper();
          while (parentWrapper.getWrapper() != null) {
            parentWrapper = parentWrapper.getWrapper();
          }
          for (int i = parentWrapper.getChildCount() - 1; i >= 0; i--) {
            nestedNode.add((MutableTreeNode)parentWrapper.getChildAt(i));
          }
          ((DirectoryNode)directoryNode).setCompactedDirNode(null);
          parentWrapper.add(nestedNode);
          nestedNode.removeUpReference();
          return parentWrapper;
        }
        if (directoryNode.getParent() == null) {    //find first node in tree
          DirectoryNode parentWrapper = ((DirectoryNode)directoryNode).getWrapper();
          if (parentWrapper != null) {
            while (parentWrapper.getWrapper() != null) {
              parentWrapper = parentWrapper.getWrapper();
            }
            return parentWrapper;
          }
        }
      }
      return directoryNode;
    }

    final VirtualFile virtualFile = psiDirectory.getVirtualFile();

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    final VirtualFile sourceRoot = fileIndex.getSourceRootForFile(virtualFile);
    final VirtualFile contentRoot = fileIndex.getContentRootForFile(virtualFile);

    directoryNode = new DirectoryNode(psiDirectory, myCompactEmptyMiddlePackages, myFlattenPackages);
    getMap(myModuleDirNodes, scopeType).put(psiDirectory, (DirectoryNode)directoryNode);

    final PsiDirectory directory = psiDirectory.getParentDirectory();
    if (!myFlattenPackages && directory != null) {
      if (myCompactEmptyMiddlePackages && sourceRoot != virtualFile && contentRoot != virtualFile) {//compact
        ((DirectoryNode)directoryNode).setCompactedDirNode(childNode);
      }
      final VirtualFile parentDir = directory.getVirtualFile();
      if (fileIndex.getModuleForFile(parentDir) == module) {
        DirectoryNode parentDirectoryNode = getMap(myModuleDirNodes, scopeType).get(directory);
        if (parentDirectoryNode != null
            || !myCompactEmptyMiddlePackages
            || parentDir == sourceRoot || parentDir == contentRoot) {
          getModuleDirNode(directory, module, scopeType, (DirectoryNode)directoryNode).add(directoryNode);
        }
        else {
          directoryNode = getModuleDirNode(directory, module, scopeType, (DirectoryNode)directoryNode);
        }
      }
      else {
        getModuleNode(module, scopeType).add(directoryNode);
      }
    }
    else {
      if (contentRoot == virtualFile) {
        getModuleNode(module, scopeType).add(directoryNode);
      } else {
        final PsiDirectory root;
        if (sourceRoot != virtualFile && sourceRoot != null) {
          root = myPsiManager.findDirectory(sourceRoot);
        } else if (contentRoot != null) {
          root = myPsiManager.findDirectory(contentRoot);
        } else {
          root = null;
        }
        if (root != null) {
          getModuleDirNode(root, module, scopeType, null).add(directoryNode);
        }
      }
    }

    return directoryNode;
  }


  @Nullable
  private PackageDependenciesNode getModuleNode(Module module, ScopeType scopeType) {
    if (module == null || !myShowModules) {
      return getRootNode(scopeType);
    }
    ModuleNode node = getMap(myModuleNodes, scopeType).get(module);
    if (node != null) return node;
    node = new ModuleNode(module);
    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    final String[] groupPath = moduleManager.getModuleGroupPath(module);
    if (groupPath == null) {
      getMap(myModuleNodes, scopeType).put(module, node);
      getRootNode(scopeType).add(node);
      return node;
    }
    getMap(myModuleNodes, scopeType).put(module, node);
    if (myShowModuleGroups) {
      getParentModuleGroup(groupPath, scopeType).add(node);
    } else {
      getRootNode(scopeType).add(node);
    }
    return node;
  }

  private PackageDependenciesNode getParentModuleGroup(String [] groupPath, ScopeType scopeType){
    ModuleGroupNode groupNode = getMap(myModuleGroupNodes, scopeType).get(groupPath[groupPath.length - 1]);
    if (groupNode == null) {
      groupNode = new ModuleGroupNode(new ModuleGroup(groupPath));
      getMap(myModuleGroupNodes, scopeType).put(groupPath[groupPath.length - 1], groupNode);
      getRootNode(scopeType).add(groupNode);
    }
    if (groupPath.length > 1) {
      String [] path = new String[groupPath.length - 1];
      System.arraycopy(groupPath, 0, path, 0, groupPath.length - 1);
      final PackageDependenciesNode node = getParentModuleGroup(path, scopeType);
      node.add(groupNode);
    }
    return groupNode;
  }

  private PackageDependenciesNode getLibraryOrJDKNode(OrderEntry libraryOrJdk) {
    if (libraryOrJdk == null || !myShowModules) {
      return getRootNode(ScopeType.LIB);
    }

    if (!myShowIndividualLibs) {
      if (myGroupByScopeType) return getRootNode(ScopeType.LIB);
      if (myAllLibsNode == null) {
        myAllLibsNode = new GeneralGroupNode(AnalysisScopeBundle.message("dependencies.libraries.node.text"), LIB_ICON_OPEN, LIB_ICON_CLOSED);
        getRootNode(ScopeType.LIB).add(myAllLibsNode);
      }
      return myAllLibsNode;
    }

    LibraryNode node = getMap(myLibraryNodes, ScopeType.LIB).get(libraryOrJdk);
    if (node != null) return node;
    node = new LibraryNode(libraryOrJdk);
    getMap(myLibraryNodes, ScopeType.LIB).put(libraryOrJdk, node);

    getRootNode(ScopeType.LIB).add(node);
    return node;
  }


  @NotNull
  public PackageDependenciesNode getRootNode(ScopeType scopeType) {
    if (!myGroupByScopeType) {
      return myRoot;
    }
    else {
      if (scopeType == ScopeType.TEST) {
        return myTestRoot;
      }
      else if (scopeType == ScopeType.SOURCE) {
        return mySourceRoot;
      }
      else {
        return myLibsRoot;
      }
    }
  }
}
