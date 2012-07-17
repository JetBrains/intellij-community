package com.intellij.util.xml.config;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class ConfigFileSearcher {

  private final MultiMap<Module, PsiFile> myFiles = new MultiMap<Module, PsiFile>();
  private final MultiMap<VirtualFile, PsiFile> myJars = new MultiMap<VirtualFile, PsiFile>();
  private final List<VirtualFile> myVirtualFiles = new ArrayList<VirtualFile>();
  private final @NotNull Module myModule;

  public ConfigFileSearcher(@NotNull Module module) {
    myModule = module;
  }

  public void search() {
    myFiles.clear();
    myJars.clear();

    for (PsiFile file : search(myModule)) {
      VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(file.getVirtualFile());
      if (jar != null) {
        myJars.putValue(jar, file);
      }
      else {
        Module module = ModuleUtilCore.findModuleForPsiElement(file);
        if (module != null) {
          myFiles.putValue(module, file);
        }
      }
    }
  }

  public abstract Set<PsiFile> search(@NotNull Module module);

  public MultiMap<Module, PsiFile> getFilesByModules() {
    return myFiles;
  }

  public MultiMap<VirtualFile, PsiFile> getJars() {
    return myJars;
  }

  public List<VirtualFile> getVirtualFiles() {
    return myVirtualFiles;
  }
}
