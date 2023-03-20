// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeInsight;

import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Collections;

public class ShResolveScopeProviderTest extends BasePlatformTestCase {
  public void testResolveScopeShell() {
    PsiFile file = myFixture.configureByText("test.sh", "");
    GlobalSearchScope expected = GlobalSearchScope.filesScope(getProject(), Collections.singletonList(file.getVirtualFile()));
    assertEquals(expected, file.getResolveScope());
  }
}