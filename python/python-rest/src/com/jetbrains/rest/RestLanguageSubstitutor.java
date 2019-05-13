/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
