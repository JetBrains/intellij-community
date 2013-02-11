/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.intellij.lang.xpath.xslt.impl.references;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.xpath.xslt.context.XsltNamespaceContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PrefixReference extends SimpleAttributeReference implements EmptyResolveMessageProvider {

  private final TextRange myRange;

  public PrefixReference(XmlAttribute attribute) {
    super(attribute);
    myRange = getPrefixRange(myAttribute);
  }

  public PrefixReference(XmlAttribute attribute, TextRange range) {
    super(attribute);
    myRange = range;
  }

  public static TextRange getPrefixRange(XmlAttribute attribute) {
    final String value = attribute.getValue();
    final int p = value.indexOf(':');
    if (p == -1) {
      return TextRange.from(0, 0);
    } else {
      return TextRange.from(0, p);
    }
  }

  public boolean isSoft() {
    return false;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  @NotNull
  protected TextRange getTextRange() {
    return myRange;
  }

  @Override
  @Nullable
  public PsiElement resolveImpl() {
    return XsltNamespaceContext.resolvePrefix(getCanonicalText(), myAttribute);
  }

  @NotNull
  public String getUnresolvedMessagePattern() {
    return "Undeclared namespace prefix ''{0}''";
  }
}