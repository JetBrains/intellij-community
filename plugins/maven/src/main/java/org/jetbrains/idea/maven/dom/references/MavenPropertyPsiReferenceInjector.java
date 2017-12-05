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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomReferenceInjector;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

public class MavenPropertyPsiReferenceInjector implements DomReferenceInjector {
  @Override
  public String resolveString(@Nullable String unresolvedText, @NotNull ConvertContext context) {
    if (StringUtil.isEmptyOrSpaces(unresolvedText)) return unresolvedText;
    MavenDomProjectModel model = (MavenDomProjectModel)DomUtil.getFileElement(context.getInvocationElement()).getRootElement();
    return MavenPropertyResolver.resolve(unresolvedText, model);
  }

  @Override
  @NotNull
  public PsiReference[] inject(@Nullable String unresolvedText, @NotNull PsiElement element, @NotNull ConvertContext context) {
    return MavenPropertyPsiReferenceProvider.getReferences(element, true);
  }
}
