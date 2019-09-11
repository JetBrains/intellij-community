/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Context for resolving Python qualified names with various options.
 *
 * @author vlan
 */
public interface PyQualifiedNameResolveContext {
  @Nullable
  PsiElement getFoothold();
  int getRelativeLevel();
  @Nullable
  Sdk getSdk();
  @Nullable
  Module getModule();
  @NotNull
  Project getProject();
  boolean getWithoutRoots();
  boolean getWithoutForeign();
  boolean getWithoutStubs();
  @NotNull
  PsiManager getPsiManager();
  boolean getWithMembers();
  boolean getWithPlainDirectories();
  boolean getVisitAllModules();
  @Nullable
  Sdk getEffectiveSdk();

  boolean isValid();
  @Nullable
  PsiFile getFootholdFile();
  @Nullable
  PsiDirectory getContainingDirectory();

  @NotNull
  PyQualifiedNameResolveContext copyWithoutForeign();
  @NotNull
  PyQualifiedNameResolveContext copyWithMembers();
  @NotNull
  PyQualifiedNameResolveContext copyWithPlainDirectories();
  @NotNull
  PyQualifiedNameResolveContext copyWithRelative(int relativeLevel);
  @NotNull
  PyQualifiedNameResolveContext copyWithoutRoots();
  @NotNull
  PyQualifiedNameResolveContext copyWithRoots();
  @NotNull
  PyQualifiedNameResolveContext copyWithoutStubs();
}