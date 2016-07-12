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
package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.psi.PropertiesList;
import com.intellij.lang.properties.psi.PropertiesListStub;
import com.intellij.psi.PsiElement;

/**
 * @author max
 */
public class PropertiesListImpl extends PropertiesStubElementImpl<PropertiesListStub> implements PropertiesList {
  public PropertiesListImpl(final ASTNode node) {
    super(node);
  }

  public PropertiesListImpl(final PropertiesListStub stub) {
    super(stub, PropertiesElementTypes.PROPERTIES_LIST);
  }

  public String toString() {
    return "PropertiesList";
  }

  @Override
  public PsiElement getParent() {
    return getParentByStub();
  }
}
