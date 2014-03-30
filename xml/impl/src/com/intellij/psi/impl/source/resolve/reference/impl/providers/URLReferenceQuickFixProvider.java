/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class URLReferenceQuickFixProvider extends UnresolvedReferenceQuickFixProvider<URLReference> {
  @Override
  public void registerFixes(@NotNull URLReference ref, @NotNull QuickFixActionRegistrar registrar) {
    registrar.register(new FetchExtResourceAction());
    registrar.register(new ManuallySetupExtResourceAction());
    registrar.register(new IgnoreExtResourceAction());

    final PsiElement parentElement = ref.getElement().getParent();
    if (parentElement instanceof XmlAttribute && ((XmlAttribute)parentElement).isNamespaceDeclaration()) {
      registrar.register(new AddXsiSchemaLocationForExtResourceAction());
    }
  }

  @NotNull
  @Override
  public Class<URLReference> getReferenceClass() {
    return URLReference.class;
  }
}
