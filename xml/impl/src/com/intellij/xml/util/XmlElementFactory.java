/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;

/**
 * @author mike
 */
public class XmlElementFactory {
  private XmlElementFactory() {
  }

  public static XmlText createXmlTextFromText(PsiManager psiManager, String text) throws IncorrectOperationException {
    return com.intellij.psi.XmlElementFactory.getInstance(psiManager.getProject()).createTagFromText("<foo>" + text + "</foo>").getValue().getTextElements()[0];
  }
}
