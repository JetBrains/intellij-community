// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolvingHint;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ReflectionUtil;
import com.intellij.xml.impl.XmlEnumerationDescriptor;
import com.intellij.xml.psi.XmlPsiBundle;
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

  @Override
  public Object @NotNull [] getVariants() {
    if (myDescriptor.isFixed()) {
      String defaultValue = myDescriptor.getDefaultValue();
      return defaultValue == null ? ArrayUtilRt.EMPTY_OBJECT_ARRAY : new Object[] {defaultValue};
    }
    else {
      String[] values = myDescriptor.getValuesForCompletion();
      return values == null ? ArrayUtilRt.EMPTY_OBJECT_ARRAY : values;
    }
  }

  @NotNull
  @Override
  public String getUnresolvedMessagePattern() {
    String name = getElement() instanceof XmlTag ? "tag" : "attribute";
    return myDescriptor.isFixed()
           ? XmlPsiBundle.message("xml.inspections.should.have.fixed.value", StringUtil.capitalize(name), myDescriptor.getDefaultValue())
           : XmlPsiBundle.message("xml.inspections.wrong.value", name);
  }
}
