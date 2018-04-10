/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.references;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import org.jetbrains.annotations.NotNull;

public class MavenUsageTargetProvider implements UsageTargetProvider {
  public UsageTarget[] getTargets(@NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement target = MavenTargetUtil.getFindTarget(editor, file);
    if (target == null) return UsageTarget.EMPTY_ARRAY;
    return new UsageTarget[]{new PsiElement2UsageTargetAdapter(target)};
  }

  public UsageTarget[] getTargets(@NotNull PsiElement psiElement) {
    return UsageTarget.EMPTY_ARRAY;
  }
}
