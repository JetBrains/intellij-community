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

import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class MavenPropertyCompletionContributor extends CompletionContributor {
  @Override
  public void beforeCompletion(@NotNull CompletionInitializationContext context) {
    Project project = context.getProject();
    PsiFile psiFile = context.getFile();

    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    if (!manager.isMavenizedProject()) return;

    MavenProject projectFile = MavenDomUtil.findContainingProject(psiFile);
    if (projectFile == null) return;

    if (!MavenDomUtil.isMavenFile(psiFile) && !MavenDomUtil.isFilteredResourceFile(psiFile)) return;

    CharSequence text = context.getEditor().getDocument().getCharsSequence();
    int offset = context.getStartOffset();
    if (isAfterOpenBrace(text, offset)) {
      context.setFileCopyPatcher(new DummyIdentifierPatcher(CompletionInitializationContext.DUMMY_IDENTIFIER.trim() + "}"));
    }
  }

  private boolean isAfterOpenBrace(CharSequence text, int offset) {
    for (int i = offset - 1; i > 0; i--) {
      char c = text.charAt(i);
      if (c == '{' && text.charAt(i - 1) == '$') return true;
      if (!Character.isLetterOrDigit(c) && c != '.') return false;
    }
    return false;
  }

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
    super.fillCompletionVariants(parameters, result);
  }
}
