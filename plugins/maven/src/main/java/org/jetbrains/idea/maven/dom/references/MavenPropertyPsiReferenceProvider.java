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

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class MavenPropertyPsiReferenceProvider extends PsiReferenceProvider {
  private final boolean myFiltered;

  public MavenPropertyPsiReferenceProvider(boolean filtered) {
    myFiltered = filtered;
  }

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    if (myFiltered) {
      if (!MavenDomUtil.isFilteredResourceFile(element)) return PsiReference.EMPTY_ARRAY;
      return getReferences(element, element.getText(), 0, true, false);
    }
    return getReferences(element, false);
  }

  public static PsiReference[] getReferences(PsiElement element, boolean isSoft) {
    String text = ElementManipulators.getValueText(element);
    int textStart = ElementManipulators.getValueTextRange(element).getStartOffset();
    return getReferences(element, text, textStart, false, isSoft);
  }

  private static PsiReference[] getReferences(PsiElement element, String text, int textStart, boolean isFiltered, boolean isSoft) {
    if (StringUtil.isEmptyOrSpaces(text)) return PsiReference.EMPTY_ARRAY;

    MavenProject mavenProject = MavenDomUtil.findContainingProject(element);
    if (mavenProject == null) return PsiReference.EMPTY_ARRAY;

    List<PsiReference> result = new ArrayList<PsiReference>();

    Matcher matcher = MavenPropertyResolver.PATTERN.matcher(text);
    while (matcher.find()) {
      String propertyName = matcher.group(1);
      int from = textStart + matcher.start(1);
      TextRange range = TextRange.from(from, propertyName.length());

      MavenPropertyPsiReference ref;
      if (isFiltered) {
        ref = new MavenFilteredPropertyPsiReference(mavenProject, element, propertyName, range, isSoft);
      }
      else {
        ref = new MavenPropertyPsiReference(mavenProject, element, propertyName, range, isSoft);
      }
      result.add(ref);
    }

    return result.toArray(new PsiReference[result.size()]);
  }
}
