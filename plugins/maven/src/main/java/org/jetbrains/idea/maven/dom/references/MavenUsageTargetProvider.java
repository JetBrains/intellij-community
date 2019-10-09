// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.references;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import org.jetbrains.annotations.NotNull;

public class MavenUsageTargetProvider implements UsageTargetProvider {
  @Override
  public UsageTarget[] getTargets(@NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement target = MavenTargetUtil.getFindTarget(editor, file, false);
    if (target == null) return UsageTarget.EMPTY_ARRAY;
    if (target instanceof NavigationItem) return new UsageTarget[]{new PsiElement2UsageTargetAdapter(target)};
    return UsageTarget.EMPTY_ARRAY;
  }
}
