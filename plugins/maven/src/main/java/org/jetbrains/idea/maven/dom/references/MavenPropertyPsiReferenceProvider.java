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
  public static final boolean SOFT_DEFAULT = false;

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    return getReferences(element, SOFT_DEFAULT);
  }

  public static PsiReference[] getReferences(PsiElement element, boolean isSoft) {
    TextRange textRange = ElementManipulators.getValueTextRange(element);
    if (textRange.isEmpty()) return PsiReference.EMPTY_ARRAY;

    String text = element.getText();

    if (StringUtil.isEmptyOrSpaces(text)) return PsiReference.EMPTY_ARRAY;

    MavenProject mavenProject = MavenDomUtil.findContainingProject(element);
    if (mavenProject == null) return PsiReference.EMPTY_ARRAY;

    List<PsiReference> result = new ArrayList<PsiReference>();

    Matcher matcher = MavenPropertyResolver.PATTERN.matcher(textRange.substring(text));
    while (matcher.find()) {
      String propertyName = matcher.group(1);
      int from = textRange.getStartOffset() + matcher.start(1);
      TextRange range = TextRange.from(from, propertyName.length());

      result.add(new MavenPropertyPsiReference(mavenProject, element, propertyName, range, isSoft));
    }

    return result.toArray(new PsiReference[result.size()]);
  }
}
