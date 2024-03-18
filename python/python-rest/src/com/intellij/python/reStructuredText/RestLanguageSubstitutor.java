// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText;

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
public final class RestLanguageSubstitutor extends LanguageSubstitutor {
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
