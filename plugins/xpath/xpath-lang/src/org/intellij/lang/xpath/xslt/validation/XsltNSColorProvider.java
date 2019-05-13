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
package org.intellij.lang.xpath.xslt.validation;

import com.intellij.codeInsight.daemon.impl.analysis.XmlNSColorProvider;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class XsltNSColorProvider implements XmlNSColorProvider {

  @Nullable
  @Override
  public TextAttributesKey getKeyForNamespace(String namespace, XmlElement context) {
    if (!(context instanceof XmlTag)) return null;
    if (XsltSupport.XSLT_NS.equals(((XmlTag)context).getNamespace())) return XsltSupport.XSLT_DIRECTIVE;
    return null;
  }
}
