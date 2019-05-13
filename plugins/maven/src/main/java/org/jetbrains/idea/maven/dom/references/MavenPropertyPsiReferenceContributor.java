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
package org.jetbrains.idea.maven.dom.references;

import com.intellij.patterns.DomPatterns;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.model.MavenDomProperties;

public class MavenPropertyPsiReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    ElementPattern pattern = XmlPatterns.xmlTag().withParent(DomPatterns.tagWithDom("properties", DomPatterns.domElement(MavenDomProperties.class)));
    registrar.registerReferenceProvider(pattern, new MavenPropertyPsiReferenceProvider(), PsiReferenceRegistrar.DEFAULT_PRIORITY);
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(),
                                        new MavenFilteredPropertyPsiReferenceProvider(),
                                        PsiReferenceRegistrar.DEFAULT_PRIORITY);
  }
}
