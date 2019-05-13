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
package com.intellij.psi.filters.position;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.xml.*;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;

public class NamespaceFilter implements ElementFilter {
  private final String[] myNamespaces;

  public NamespaceFilter(@NonNls String... namespaces){
    myNamespaces = namespaces;
  }

  @Override
  public boolean isClassAcceptable(Class hintClass){
    return ReflectionUtil.isAssignable(XmlTag.class, hintClass) || ReflectionUtil.isAssignable(XmlDocument.class, hintClass);
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof XmlTag){
      final XmlTag psiElement = (XmlTag)element;
      if (!psiElement.isValid()) return false;
      final String ns = psiElement.getNamespace();

      if (isNamespaceAcceptable(ns)) return true;

      final PsiFile psiFile = psiElement.getContainingFile();
      if (psiFile instanceof XmlFile) {
        // We use file references for as dtd namespace
        // But we should also check PUBLIC ID for namespace
        XmlDocument document = ((XmlFile)psiFile).getDocument();
        if (document == null) return false;
        final XmlProlog prolog = document.getProlog();

        if (prolog != null) {
          final XmlDoctype doctype = prolog.getDoctype();
          if (doctype != null) {
            final String publicId = doctype.getPublicId();

            if (publicId != null) {
              if (isNamespaceAcceptable(publicId)) return true;
            }
          }
        }
      }
    }
    else if(element instanceof XmlDocument){
      return isAcceptable(((XmlDocument) element).getRootTag(), context);
    }
    return false;
  }

  protected boolean isNamespaceAcceptable(String ns) {
    for (String aMyValue : myNamespaces) {
      if (aMyValue.equals(ns)) return true;
    }
    return false;
  }
}
