/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.xml.dom.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.*;
import org.intellij.plugins.relaxNG.ApplicationLoader;
import org.intellij.plugins.relaxNG.references.FileReferenceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 18.08.2007
 */
public class RngHrefConverter extends Converter<XmlFile> implements CustomReferenceConverter<XmlFile> {
  public XmlFile fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s != null) {
      final GenericAttributeValue<XmlFile> element = (GenericAttributeValue<XmlFile>)context.getInvocationElement();
      final PsiReference[] references = createReferences(element, element.getXmlAttributeValue(), context);
      if (references.length > 0) {
        PsiElement result = references[references.length - 1].resolve();
        if (result instanceof XmlFile) {
          return (XmlFile)result;
        }
      }
    }
    return null;
  }

  public String toString(@Nullable XmlFile psiFile, ConvertContext context) {
    return psiFile == null ? null : psiFile.getName();
  }

  @NotNull
  public PsiReference[] createReferences(GenericDomValue<XmlFile> genericDomValue, PsiElement element, ConvertContext context) {
    final String s = genericDomValue.getStringValue();
    if (s == null || element == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final FileReferenceSet set = FileReferenceSet.createSet(element, false, false, false);
    if (set != null) {
      return FileReferenceUtil.restrict(set, FileReferenceUtil.byNamespace(ApplicationLoader.RNG_NAMESPACE), true);
    } else {
      return PsiReference.EMPTY_ARRAY;
    }
  }
}
