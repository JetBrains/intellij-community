// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext.python;

import com.intellij.lang.Language;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutor;
import com.intellij.restructuredtext.RestLanguage;
import com.jetbrains.python.ReSTService;
import org.jetbrains.annotations.NotNull;

final class RestLanguageSubstitutor extends LanguageSubstitutor {
  @Override
  public Language getLanguage(final @NotNull VirtualFile vFile, final @NotNull Project project) {
    if (vFile.getFileSystem() instanceof NonPhysicalFileSystem) {
      return null;
    }

    Module module = ModuleUtilCore.findModuleForFile(vFile, project);
    if (module == null || module.isDisposed()) {
      return null;
    }
    return ReSTService.getInstance(module).txtIsRst() ? RestLanguage.INSTANCE : null;
  }
}
