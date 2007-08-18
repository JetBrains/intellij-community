/*
 * User: anna
 * Date: 22-Mar-2007
 */
package com.intellij.ide.navigationToolbar;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class NavBarModel {
  private ArrayList<Object> myModel = new ArrayList<Object>();
  private int mySelectedIndex;
  private Project myProject;

  public NavBarModel(final Project project) {
    myProject = project;
  }

  public void addElement(Object object) {
    myModel.add(object);
  }

  public void removeAllElements() {
    myModel.clear();
  }

  public int getSelectedIndex() {
    return mySelectedIndex;
  }

  @Nullable
  public Object getSelectedValue() {
    return getElement(mySelectedIndex);
  }

  @Nullable
  public Object getElement(int index) {
    if (index != -1 && index < myModel.size()) {
      return myModel.get(index);
    }
    return null;
  }

  public int size() {
    return myModel.size();
  }

  public boolean isEmpty() {
    return myModel.isEmpty();
  }

  public int getIndexByMode(int index) {
    if (index < 0) return myModel.size() + index;
    if (index >= myModel.size() && myModel.size() > 0) return index % myModel.size();
    return index;
  }

  protected boolean updateModel(DataContext dataContext) {
    PsiElement psiElement = (PsiElement)dataContext.getData(DataConstants.PSI_FILE);
    psiElement = normalize(psiElement);
    if (psiElement != null && psiElement.isValid()) {
      return updateModel(psiElement);
    }
    else {
      final Module module = DataKeys.MODULE_CONTEXT.getData(dataContext);
      if (module != null) {
        if (size() == 1 && getElement(0) == module) { //no need to update
          return false;
        }
        removeAllElements();
        addElement(module);
      }
    }
    return true;
  }

  protected boolean updateModel(final PsiElement psiElement) {
    final int oldModelSize = size();
    final List<Object> oldModel = new ArrayList<Object>();
    for (int i = 0; i < oldModelSize; i++) {
      oldModel.add(getElement(i));
    }
    removeAllElements();
    addElement(myProject);
    final Set<VirtualFile> roots = new HashSet<VirtualFile>();
    final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(myProject);
    Module module = ModuleUtil.findModuleForPsiElement(psiElement);
    final ProjectFileIndex projectFileIndex = projectRootManager.getFileIndex();
    if (module != null) {
      VirtualFile vFile = PsiUtil.getVirtualFile(psiElement);
      if (vFile != null && (projectFileIndex.isInLibrarySource(vFile) || projectFileIndex.isInLibraryClasses(vFile))) {
        module = null;
      }
    }
    if (module == null) {
      roots.addAll(Arrays.asList(projectRootManager.getContentRoots()));
    }
    else {
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      roots.addAll(Arrays.asList(moduleRootManager.getContentRoots()));
      addElement(module);
    }
    traverseToRoot(psiElement, roots);
    if (oldModelSize == size()) {
      for (int i = 0; i < oldModelSize; i++) {
        if (!Comparing.equal(oldModel.get(i), getElement(i))) return true;
      }
      return false;
    }
    else {
      return true;
    }
  }

  public void updateModel(final Object object) {
    if (object instanceof PsiElement) {
      final Object rootElement = size() > 1 ? getElement(1) : null;
      if (rootElement instanceof Module) {
        final Module module = (Module)rootElement;
        removeAllElements();
        addElement(myProject);
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        addElement(module);
        traverseToRoot((PsiElement)object, new HashSet<VirtualFile>(Arrays.asList(moduleRootManager.getContentRoots())));
      }
      else {
        updateModel((PsiElement)object);
      }
    }
    else if (object instanceof Module) {
      removeAllElements();
      addElement(myProject);
      addElement(object);
    }
  }

  private void traverseToRoot(@NotNull PsiElement psiElement, Set<VirtualFile> roots) {
    if (!psiElement.isValid()) return;
    final PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile != null && containingFile.getVirtualFile() == null) return; //non phisycal elements
    psiElement = psiElement.getOriginalElement();
    PsiElement resultElement = psiElement;
    if (containingFile != null) {
      if (!(psiElement instanceof PsiClass)) {
        resultElement = containingFile;
      }
      final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
      if (containingDirectory != null) {
        traverseToRoot(containingDirectory, roots);
      }
    }
    else if (psiElement instanceof PsiDirectory) {
      final PsiDirectory psiDirectory = (PsiDirectory)psiElement;
      final PsiDirectory parentDirectory = psiDirectory.getParentDirectory();
      if (!roots.contains(psiDirectory.getVirtualFile()) && parentDirectory != null) {
        traverseToRoot(parentDirectory, roots);
      }
    }
    else if (psiElement instanceof PsiPackage) {
      final PsiPackage psiPackage = (PsiPackage)psiElement;
      final PsiPackage parentPackage = psiPackage.getParentPackage();
      if (parentPackage != null) {
        final String qualifiedName = parentPackage.getQualifiedName();
        if (qualifiedName.length() > 0) {
          traverseToRoot(parentPackage, roots);
        }
      }
    }
    addElement(resultElement);
  }


  protected boolean hasChildren(Object object) {
    if (!checkValid(object)) return false;
    if (object instanceof Project) {
      return ModuleManager.getInstance((Project)object).getModules().length > 0;
    }
    if (object instanceof Module) {
      final Module module = (Module)object;
      if (module.isDisposed()) return false;
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      return moduleRootManager.getContentRoots().length > 0;
    }
    if (object instanceof PsiClass || object instanceof PsiFile) {
      return false;
    }
    if (object instanceof PsiDirectory) {
      final List<Object> result = new ArrayList<Object>();
      final Object rootElement = size() > 1 ? getElement(1) : null;
      if (rootElement instanceof Module && ((Module)rootElement).isDisposed()) return false;
      getDirectoryChildren((PsiDirectory)object, rootElement, result);
      return result.size() > 0;
    }
    if (object instanceof OrderEntry) {
      final JdkOrderEntry entry = (JdkOrderEntry)object;
      return entry.getFiles(OrderRootType.SOURCES).length > 0 || entry.getFiles(OrderRootType.CLASSES).length > 0;
    }
    return false;
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  private static boolean checkValid(Object object) {
    if (object instanceof Project) {
      return !((Project)object).isDisposed();
    }
    if (object instanceof Module) {
      return !((Module)object).isDisposed();
    }
    if (object instanceof PsiElement) {
      return ((PsiElement)object).isValid();
    }
    return true;
  }

  protected String getPresentableText(Object object, Window window) {
    if (!checkValid(object)) return IdeBundle.message("node.structureview.invalid");
    if (object instanceof Project) {
      return wrapPresentation(((Project)object).getName(), window);
    }
    else if (object instanceof Module) {
      return wrapPresentation(((Module)object).getName(), window);
    }
    else if (object instanceof PsiClass) {
      return wrapPresentation(ClassPresentationUtil.getNameForClass((PsiClass)object, false), window);
    }
    else if (object instanceof PsiFile) {
      return wrapPresentation(((PsiFile)object).getName(), window);
    }
    else if (object instanceof PsiDirectory) {
      return wrapPresentation(((PsiDirectory)object).getVirtualFile().getName(), window);
    }
    else if (object instanceof PsiPackage) {
      final String name = ((PsiPackage)object).getName();
      return wrapPresentation(name != null ? name : AnalysisScopeBundle.message("dependencies.tree.node.default.package.abbreviation"), window);
    }
    else if (object instanceof JdkOrderEntry) {
      return wrapPresentation(((JdkOrderEntry)object).getJdkName(), window);
    }
    else if (object instanceof LibraryOrderEntry) {
      final String libraryName = ((LibraryOrderEntry)object).getLibraryName();
      return wrapPresentation(libraryName != null ? libraryName : AnalysisScopeBundle.message("package.dependencies.library.node.text"), window);
    }
    else if (object instanceof ModuleOrderEntry) {
      final ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)object;
      return wrapPresentation(moduleOrderEntry.getModuleName(), window);
    }
    return null;
  }

  private static String wrapPresentation(String result, Window window) {
    if (result != null) {
      boolean trancated = false;
      if (window != null) {
        final int windowWidth = window.getWidth();
        while (window.getFontMetrics(window.getFont()).stringWidth(result) + 100 > windowWidth && result.length() > 10) {
          result = result.substring(0, result.length() - 10);
          trancated = true;
        }
      }
      return result + (trancated ? "..." : "");
    }
    return result;
  }

  protected static Icon getIcon(Object object) {
    if (!checkValid(object)) return null;
    if (object instanceof Project) return IconLoader.getIcon("/nodes/project.png");
    if (object instanceof Module) return ((Module)object).getModuleType().getNodeIcon(false);
    if (object instanceof PsiElement && ((PsiElement)object).isValid()) return ((PsiElement)object).getIcon(Iconable.ICON_FLAG_CLOSED);
    if (object instanceof JdkOrderEntry) return ((JdkOrderEntry)object).getJdk().getSdkType().getIcon();
    if (object instanceof LibraryOrderEntry) return IconLoader.getIcon("/nodes/ppLibClosed.png");
    if (object instanceof ModuleOrderEntry) return ((ModuleOrderEntry)object).getModule().getModuleType().getNodeIcon(false);
    return null;
  }

  protected SimpleTextAttributes getTextAttributes(final Object object, final boolean selected) {
    if (!checkValid(object)) return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    if (object instanceof PsiElement) {
      if (!((PsiElement)object).isValid()) return SimpleTextAttributes.GRAYED_ATTRIBUTES;
      PsiFile psiFile = ((PsiElement)object).getContainingFile();
      if (psiFile != null) {
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        return new SimpleTextAttributes(null, selected ? null : FileStatusManager.getInstance(myProject).getStatus(virtualFile).getColor(),
                                        Color.red, WolfTheProblemSolver.getInstance(myProject).isProblemFile(virtualFile)
                                                   ? SimpleTextAttributes.STYLE_WAVED
                                                   : SimpleTextAttributes.STYLE_PLAIN);
      }
      else {
        return new SimpleTextAttributes(null, null, Color.red, WolfTheProblemSolver.getInstance(myProject)
          .hasProblemFilesBeneath((PsiElement)object)
                                                               ? SimpleTextAttributes.STYLE_WAVED
                                                               : SimpleTextAttributes.STYLE_PLAIN);
      }
    }
    else if (object instanceof Module) {
      return new SimpleTextAttributes(null, null, Color.red, WolfTheProblemSolver.getInstance(myProject)
        .hasProblemFilesBeneath((Module)object)
                                                             ? SimpleTextAttributes.STYLE_WAVED
                                                             : SimpleTextAttributes.STYLE_PLAIN);
    }
    else if (object instanceof Project) {
      final Project project = (Project)object;
      final Module[] modules = ModuleManager.getInstance(project).getModules();
      for (Module module : modules) {
        if (WolfTheProblemSolver.getInstance(project).hasProblemFilesBeneath(module)) {
          return new SimpleTextAttributes(null, null, Color.red, SimpleTextAttributes.STYLE_WAVED);
        }
      }
    }
    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  private static void getDirectoryChildren(final PsiDirectory psiDirectory, final Object rootElement, final List<Object> result) {
    final ModuleFileIndex moduleFileIndex =
      rootElement instanceof Module ? ModuleRootManager.getInstance((Module)rootElement).getFileIndex() : null;
    final PsiElement[] children = psiDirectory.getChildren();
    for (PsiElement child : children) {
      if (child != null && child.isValid()) {
        if (moduleFileIndex != null) {
          final VirtualFile virtualFile = PsiUtil.getVirtualFile(child);
          if (virtualFile != null && !moduleFileIndex.isInContent(virtualFile)) continue;
        }
        result.add(normalize(child));
      }
    }
  }

  private static PsiElement normalize(PsiElement child) {
    if (child instanceof PsiJavaFile) {
      final PsiJavaFile psiJavaFile = (PsiJavaFile)child;
      if (psiJavaFile.getViewProvider().getBaseLanguage() == StdLanguages.JAVA) {
        final PsiClass[] psiClasses = psiJavaFile.getClasses();
        if (psiClasses.length == 1) {
          child = psiClasses[0];
        }
      }
    }
    return child;
  }

  List<Object> calcElementChildren(final Object object) {
    final List<Object> result = new ArrayList<Object>();
    Object rootElement = size() > 1 ? getElement(1) : null;
    if (!(object instanceof Project) && rootElement instanceof Module && ((Module)rootElement).isDisposed()) return result;
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    if (object instanceof Project) {
      result.addAll(Arrays.asList(ModuleManager.getInstance((Project)object).getModules()));
    }
    else if (object instanceof Module) {
      Module module = (Module)object;
      if (!module.isDisposed()) {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        VirtualFile[] roots = moduleRootManager.getContentRoots();
        for (VirtualFile root : roots) {
          final PsiDirectory psiDirectory = psiManager.findDirectory(root);
          if (psiDirectory != null) {
            result.add(psiDirectory);
          }
        }
      }
    }
    else if (object instanceof PsiPackage) {
      final PsiPackage psiPackage = (PsiPackage)object;
      final PsiDirectory[] psiDirectories = rootElement instanceof Module
                                            ? psiPackage.getDirectories(GlobalSearchScope.moduleScope((Module)rootElement))
                                            : psiPackage.getDirectories();
      for (PsiDirectory psiDirectory : psiDirectories) {
        getDirectoryChildren(psiDirectory, rootElement, result);
      }
    }
    else if (object instanceof PsiDirectory) {
      getDirectoryChildren((PsiDirectory)object, rootElement, result);
    }
    Collections.sort(result, new SiblingsComparator());
    return result;
  }

  public int indexOf(final Object object) {
    return myModel.indexOf(object);
  }

  public Object get(final int index) {
    return myModel.get(index);
  }

  public void setSelectedIndex(final int selectedIndex) {
    mySelectedIndex = selectedIndex;
  }

  private static final class SiblingsComparator implements Comparator<Object> {
    public int compare(final Object o1, final Object o2) {
      final Pair<Integer, String> w1 = getWeightedName(o1);
      final Pair<Integer, String> w2 = getWeightedName(o2);
      if (w1 == null) return w2 == null ? 0 : -1;
      if (w2 == null) return 1;
      if (w1.first != w2.first) {
        return -w1.first.intValue() + w2.first.intValue();
      }
      return w1.second.compareToIgnoreCase(w2.second);
    }

    @Nullable
    private static Pair<Integer, String> getWeightedName(Object object) {
      if (object instanceof Module) {
        return Pair.create(5, ((Module)object).getName());
      }
      if (object instanceof PsiFile) {
        return Pair.create(2, ((PsiFile)object).getName());
      }
      if (object instanceof PsiClass) {
        return Pair.create(3, ((PsiClass)object).getName());
      }
      if (object instanceof PsiPackage) {
        return Pair.create(4, ((PsiPackage)object).getName());
      }
      else if (object instanceof PsiDirectory) {
        return Pair.create(4, ((PsiDirectory)object).getName());
      }
      return null;
    }
  }
}