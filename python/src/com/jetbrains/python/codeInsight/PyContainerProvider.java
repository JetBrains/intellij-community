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
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.ContainerProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public class PyContainerProvider implements ContainerProvider {
  @Nullable
  @Override
  public PsiElement getContainer(@NotNull PsiElement item) {
    if (item instanceof PyElement && item instanceof StubBasedPsiElement) {
      return getContainerByStub((StubBasedPsiElement)item);
    }
    return null;
  }

  @Nullable
  private static PsiElement getContainerByStub(@NotNull StubBasedPsiElement element) {
    final StubElement stub = element.getStub();
    if (stub != null) {
      final StubElement parentStub = stub.getParentStub();
      if (parentStub != null) {
        return parentStub.getPsi();
      }
    }
    return null;
  }
}
