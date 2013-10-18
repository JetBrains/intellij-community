package com.jetbrains.rest;

import com.intellij.lang.Language;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutor;
import com.jetbrains.python.ReSTService;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class RestLanguageSubstitutor extends LanguageSubstitutor {
  @Override
  public Language getLanguage(@NotNull final VirtualFile vFile, @NotNull final Project project) {
    final Module module = ModuleUtilCore.findModuleForFile(vFile, project);
    if (module == null) return null;
    boolean txtIsRst = ReSTService.getInstance(module).txtIsRst();
     if (txtIsRst)
       return RestLanguage.INSTANCE;
     return null;
  }
}
