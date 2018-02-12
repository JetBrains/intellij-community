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
package com.theoryinpractice.testng;

import com.intellij.patterns.XmlAttributeValuePattern;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PathListReferenceProvider;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.XmlPatterns.*;

public class TestNGSuiteReferenceContributor extends PsiReferenceContributor {
  private static final XmlAttributeValuePattern ourTestClassPattern =
    xmlAttributeValue(xmlAttribute("name").withParent(xmlTag().withName("class")
                        .withParent(xmlTag().withName("classes")
                                      .withParent(xmlTag().withName("test").withParent(xmlTag().withName("suite"))))));

  private static final XmlAttributeValuePattern ourListenerClassPattern =
    xmlAttributeValue(xmlAttribute("class-name").withParent(xmlTag().withName("listener")
                                                              .withParent(xmlTag().withName("listeners")
                                                                            .withParent(xmlTag().withName("suite")))));

  private static final XmlAttributeValuePattern ourMethodSelectorPattern =
    xmlAttributeValue(xmlAttribute("name").withParent(xmlTag().withName("selector-class")
                                                        .withParent(xmlTag().withName("method-selector")
                                                                      .withParent(xmlTag().withName("method-selectors")
                                                                                    .withParent(
                                                                                      xmlTag().withName(string().oneOf("suite", "test")))))));

  private static final XmlAttributeValuePattern ourPackagePattern =
    xmlAttributeValue(xmlAttribute("name").withParent(xmlTag().withName("package")
                                                        .withParent(xmlTag().withName("packages")
                                                                      .withParent(xmlTag().withName("suite")))));

  private static final XmlAttributeValuePattern ourSuiteFilePattern =
    xmlAttributeValue(xmlAttribute("path").withParent(xmlTag().withName("suite-file")
                                                        .withParent(xmlTag().withName("suite-files")
                                                                      .withParent(xmlTag().withName("suite")))));

  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(ourTestClassPattern, new JavaClassReferenceProvider());
    registrar.registerReferenceProvider(ourListenerClassPattern, new JavaClassReferenceProvider());

    final JavaClassReferenceProvider methodSelectorProvider = new JavaClassReferenceProvider();
    methodSelectorProvider.setOption(JavaClassReferenceProvider.EXTEND_CLASS_NAMES, new String[]{"org.testng.IMethodSelector"});
    registrar.registerReferenceProvider(ourMethodSelectorPattern, methodSelectorProvider);

    registrar.registerReferenceProvider(ourSuiteFilePattern, new PathListReferenceProvider(){
      @Override
      protected boolean disableNonSlashedPaths() {
        return false;
      }
    });
  }
}
