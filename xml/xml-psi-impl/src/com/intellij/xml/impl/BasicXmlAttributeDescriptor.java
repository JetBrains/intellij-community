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

package com.intellij.xml.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public abstract class BasicXmlAttributeDescriptor extends XmlEnumerationDescriptor implements XmlAttributeDescriptor {
  @Override
  @Nls
  public String validateValue(XmlElement context, String value) {
    return null;
  }

  @Override
  public String getName(PsiElement context){
    return getName();
  }

  public String @Nullable [] getEnumeratedValues(@Nullable XmlElement context) {
    return getEnumeratedValues();
  }

  @Override
  public boolean isEnumerated(XmlElement context) {
    return isEnumerated();
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  protected PsiElement getEnumeratedValueDeclaration(XmlElement xmlElement, String value) {
    String[] values = getEnumeratedValues();
    if (values == null || values.length == 0) return getFirstItem(getDeclarations());
    return ArrayUtilRt.find(values, value) != -1 ? getFirstItem(getDeclarations()) : null;
  }

  @Override
  protected PsiElement getDefaultValueDeclaration() {
    return getFirstItem(getDeclarations());
  }
}
