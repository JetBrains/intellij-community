/**
 * @author cdr
 */
package com.intellij.openapi.module.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ContentEntriesEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.ide.highlighter.ModuleFileType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ModuleUtil {
  public static final Object MODULE_RENAMING_REQUESTOR = new Object();

  private ModuleUtil() {}

  public static boolean checkSourceRootsConfigured(final Module module) {
    VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
    if (sourceRoots.length == 0) {
      Messages.showErrorDialog(
          module.getProject(),
          ProjectBundle.message("module.source.roots.not.configured.error", module.getName()),
          ProjectBundle.message("module.source.roots.not.configured.title")
        );

      ModulesConfigurator.showDialog(module.getProject(), module.getName(), ContentEntriesEditor.NAME, false);

      sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
      if (sourceRoots.length == 0) {
        return false;
      }
    }
    return true;
  }

  static String moduleNameByFileName(String fileName) {
    if (fileName.endsWith(ModuleFileType.DOT_DEFAULT_EXTENSION)) {
      return fileName.substring(0, fileName.length() - ModuleFileType.DOT_DEFAULT_EXTENSION.length());
    }
    else {
      return fileName;
    }
  }

  public static String getModuleNameInReadAction(@NotNull final Module module) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return module.getName();
      }
    });
  }

  public static boolean isModuleDisposed(PsiElement element) {
    if (!element.isValid()) return true;
    final Project project = element.getProject();
    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final PsiFile file = element.getContainingFile();
    if (file == null) return true;
    VirtualFile vFile = file.getVirtualFile();
    final Module module = vFile == null ? null : projectFileIndex.getModuleForFile(vFile);
    // element may be in library
    return module == null ? !projectFileIndex.isInLibraryClasses(vFile) : module.isDisposed();
  }

  @Nullable
  public static Module getParentModuleOfType(ModuleType expectedModuleType, Module module) {
    if (expectedModuleType.equals(module.getModuleType())) return module;
    final List<Module> parents = getParentModulesOfType(expectedModuleType, module);
    return parents.size() == 0 ? null : parents.get(0);
  }

  @NotNull
  public static List<Module> getParentModulesOfType(ModuleType expectedModuleType, Module module) {
    final List<Module> parents = ModuleManager.getInstance(module.getProject()).getModuleDependentModules(module);
    ArrayList<Module> modules = new ArrayList<Module>();
    for (Module parent : parents) {
      if (expectedModuleType.equals(parent.getModuleType())) {
        modules.add(parent);
      }
    }
    return modules;
  }

  @Nullable
  public static Module findModuleForPsiElement(PsiElement element) {
    if (!element.isValid()) return null;
    Project project = element.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (element instanceof PsiPackage) {
      final PsiDirectory[] directories = ((PsiPackage)element).getDirectories();
      for (PsiDirectory directory : directories) {
        final Module module = fileIndex.getModuleForFile(directory.getVirtualFile());
        if (module != null) {
          return module;
        }
      }
      return null;
    }

    if (element instanceof PsiDirectory) {
      final VirtualFile vFile = ((PsiDirectory)element).getVirtualFile();
      if (fileIndex.isInLibrarySource(vFile) || fileIndex.isInLibraryClasses(vFile)) {
        final OrderEntry[] orderEntries = fileIndex.getOrderEntriesForFile(vFile);
        if (orderEntries.length == 0) {
          return null;
        }
        Set<Module> modules = new HashSet<Module>();
        for (OrderEntry orderEntry : orderEntries) {
          modules.add(orderEntry.getOwnerModule());
        }
        final Module[] candidates = modules.toArray(new Module[modules.size()]);
        Arrays.sort(candidates, ModuleManager.getInstance(project).moduleDependencyComparator());
        return candidates[0];
      }
      return fileIndex.getModuleForFile(vFile);
    }
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile != null) {
      VirtualFile virtualFile = containingFile.getVirtualFile();
      if (virtualFile == null) {
        PsiFile originalFile = containingFile.getOriginalFile();
        if (originalFile != null) {
          virtualFile = originalFile.getVirtualFile();
        }
      }
      if (virtualFile != null) {
        return fileIndex.getModuleForFile(virtualFile);
      }
    }
    return null;
  }

  public static Module getModuleForFile(final Project project, final VirtualFile file) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return fileIndex.getModuleForFile(file);
  }

  public static void getDependencies(Module module, Set<Module> modules) {
    if (modules.contains(module)) return;
    modules.add(module);
    Module[] dependencies = ModuleRootManager.getInstance(module).getDependencies();
    for (Module dependency : dependencies) {
      getDependencies(dependency, modules);
    }
  }
}
