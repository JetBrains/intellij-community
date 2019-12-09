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
package com.intellij.psi.impl.source.html;

import com.intellij.lang.ASTNode;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.xml.XmlTagDelegate;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Mossienko
 */
public class HtmlTagImpl extends XmlTagImpl implements HtmlTag {
  public HtmlTagImpl() {
    super(XmlElementType.HTML_TAG);
  }

  @Override
  public boolean isCaseSensitive() {
    return false;
  }

  @Nullable
  @Override
  public String getRealNs(@Nullable final String value) {
    if (XmlUtil.XHTML_URI.equals(value)) return XmlUtil.HTML_URI;
    return value;
  }

  @Override
  public String toString() {
    return "HtmlTag:" + getName();
  }


  @Override
  public XmlTag getParentTag() {
    return PsiTreeUtil.getParentOfType(this, XmlTag.class);
  }

  @NotNull
  @Override
  protected XmlTagDelegate createDelegate() {
    return new HtmlTagImplDelegate();
  }

  protected class HtmlTagImplDelegate extends HtmlTagDelegate {

    public HtmlTagImplDelegate() {
      super(HtmlTagImpl.this);
    }

    @Override
    protected void deleteChildInternalSuper(@NotNull ASTNode child) {
      HtmlTagImpl.this.deleteChildInternalSuper(child);
    }

    @Override
    protected TreeElement addInternalSuper(TreeElement first, ASTNode last, @Nullable ASTNode anchor, @Nullable Boolean before) {
      return HtmlTagImpl.this.addInternalSuper(first, last, anchor, before);
    }
  }
}
