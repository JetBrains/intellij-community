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
package com.intellij.psi.impl.source.xml;

import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlMarkupDecl;

/**
 * @author Mike
 */
public class XmlMarkupDeclImpl extends XmlElementImpl implements XmlMarkupDecl {
  public XmlMarkupDeclImpl() {
    super(XmlElementType.XML_MARKUP_DECL);
  }

  @Override
  public PsiMetaData getMetaData(){
    return MetaRegistry.getMeta(this);
  }

}
