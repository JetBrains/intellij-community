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
package com.intellij.xml.util;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolvingHint;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.xml.impl.XmlEnumerationDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Dmitry Avdeev
*/
public class XmlEnumeratedValueReference extends PsiReferenceBase<XmlElement> implements EmptyResolveMessageProvider, ResolvingHint {
  private final XmlEnumerationDescriptor myDescriptor;

  public XmlEnumeratedValueReference(XmlElement value, XmlEnumerationDescriptor descriptor) {
    super(value);
    myDescriptor = descriptor;
  }

  public XmlEnumeratedValueReference(XmlElement value, XmlEnumerationDescriptor descriptor, TextRange range) {
    super(value, range);
    myDescriptor = descriptor;
  }

  @Override
  public boolean canResolveTo(Class<? extends PsiElement> elementClass) {
    return ReflectionUtil.isAssignable(XmlElement.class, elementClass);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    return myDescriptor.getValueDeclaration(getElement(), getValue());
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    if (myDescriptor.isFixed()) {
      String defaultValue = myDescriptor.getDefaultValue();
      return defaultValue == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : new Object[] {defaultValue};
    }
    else {
      String[] values = myDescriptor.getValuesForCompletion();
      return values == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : values;
    }
  }

  @NotNull
  @Override
  public String getUnresolvedMessagePattern() {
    String name = getElement() instanceof XmlTag ? "tag" : "attribute";
    return myDescriptor.isFixed()
           ? XmlErrorMessages.message("should.have.fixed.value", StringUtil.capitalize(name), myDescriptor.getDefaultValue())
           : XmlErrorMessages.message("wrong.value", name);
  }
}
