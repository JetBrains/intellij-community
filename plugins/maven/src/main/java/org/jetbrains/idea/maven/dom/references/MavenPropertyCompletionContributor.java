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

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.Arrays;
import java.util.Collection;

public class MavenPropertyCompletionContributor extends CompletionContributor {
  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiFile psiFile = parameters.getOriginalFile();
    Project project = psiFile.getProject();

    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    if (!manager.isMavenizedProject()) return;

    MavenProject projectFile = MavenDomUtil.findContainingProject(psiFile);
    if (projectFile == null) return;

    if (!MavenDomUtil.isMavenFile(psiFile) && !MavenDomUtil.isFilteredResourceFile(psiFile)) return;

    String text = psiFile.getText();
    int offset = parameters.getOffset();
    int braceOffset = findOpenBrace(text, offset);
    if (braceOffset == -1) return;

    TextRange range = TextRange.create(braceOffset, offset);
    String prefix = range.substring(text);

    MavenFilteredPropertyPsiReference ref = new MavenFilteredPropertyPsiReference(projectFile, psiFile, prefix, range);

    addVariants(Arrays.asList(ref.getVariants()), result.withPrefixMatcher(prefix));
  }

  public static void addVariants(Collection<?> variants, CompletionResultSet result) {
    for (Object each : variants) {
      LookupElement e;
      if (each instanceof LookupElement) {
        e = (LookupElement)each;
      }
      else if (each instanceof String) {
        e = LookupElementBuilder.create((String)each);
      }
      else if (each instanceof PsiNamedElement) {
        e = LookupElementBuilder.create((PsiNamedElement)each);
      }
      else {
        e = LookupElementBuilder.create(each, String.valueOf(each));
      }
      result.addElement(e);
    }
  }

  private static int findOpenBrace(CharSequence text, int offset) {
    for (int i = offset - 1; i > 0; i--) {
      char c = text.charAt(i);
      if (c == '{' && text.charAt(i - 1) == '$') return i + 1;
      if (!Character.isLetterOrDigit(c) && c != '.') return -1;
    }
    return -1;
  }
}
