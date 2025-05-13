/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.xslt.psi.XsltCallTemplate;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.jetbrains.annotations.Nullable;

final class XsltCallTemplateImpl extends XsltTemplateInvocationBase implements XsltCallTemplate {
  XsltCallTemplateImpl(XmlTag target) {
    super(target);
  }

  @Override
  public String getTemplateName() {
    return getName();
  }

  @Override
  public @Nullable XsltTemplate getTemplate() {
    final PsiReference[] references = getReferences();
    for (PsiReference reference : references) {
      final PsiElement t = reference.resolve();
      if (t instanceof XsltTemplate) {
        return (XsltTemplate)t;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return "XsltCallTemplate: " + getName();
  }
}
