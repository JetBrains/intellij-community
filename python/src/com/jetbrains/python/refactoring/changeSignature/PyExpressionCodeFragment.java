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
package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightVirtualFile;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */

public class PyExpressionCodeFragment extends PyFileImpl implements PsiCodeFragment {

  private GlobalSearchScope myResolveScope;

  public PyExpressionCodeFragment(@NotNull final Project project,
                                  @NonNls final String name,
                                  @NotNull final CharSequence text) {
    super(((PsiManagerEx)PsiManager.getInstance(project)).getFileManager().createFileViewProvider(
      new LightVirtualFile(name, PythonFileType.INSTANCE, text), true));
    ((SingleRootFileViewProvider)getViewProvider()).forceCachedPsi(this);
  }

  @Override
  public void forceResolveScope(GlobalSearchScope scope) {
    myResolveScope = scope;
  }

  @Override
  public GlobalSearchScope getForcedResolveScope() {
    return myResolveScope;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
  }

  public boolean isAcceptedFor(@NotNull Class visitorClass) {
    return false;
  }
}
