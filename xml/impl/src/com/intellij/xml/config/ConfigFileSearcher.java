package com.intellij.xml.config;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public abstract class ConfigFileSearcher {

  private final MultiMap<Module, PsiFile> myFiles = new MultiMap<>();
  private final MultiMap<VirtualFile, PsiFile> myJars = new MultiMap<>();
  private final MultiMap<VirtualFile, PsiFile> myVirtualFiles = new MultiMap<>();
  private final @Nullable Module myModule;
  @NotNull private final Project myProject;

  public ConfigFileSearcher(@Nullable Module module, @NotNull Project project) {
    myModule = module;
    myProject = project;
  }

  public void search() {
    myFiles.clear();
    myJars.clear();

    PsiManager psiManager = PsiManager.getInstance(myProject);
    for (PsiFile file : search(myModule, myProject)) {
      VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(file.getVirtualFile());
      if (jar != null) {
        myJars.putValue(jar, file);
      }
      else {
        Module module = ModuleUtilCore.findModuleForPsiElement(file);
        if (module != null) {
          myFiles.putValue(module, file);
        }
        else {
          VirtualFile virtualFile = file.getVirtualFile();
          myVirtualFiles.putValue(virtualFile.getParent(), psiManager.findFile(virtualFile));
        }
      }
    }
  }

  public abstract Set<PsiFile> search(@Nullable Module module, @NotNull Project project);

  public MultiMap<Module, PsiFile> getFilesByModules() {
    return myFiles;
  }

  public MultiMap<VirtualFile, PsiFile> getJars() {
    return myJars;
  }

  public MultiMap<VirtualFile, PsiFile> getVirtualFiles() {
    return myVirtualFiles;
  }
}
