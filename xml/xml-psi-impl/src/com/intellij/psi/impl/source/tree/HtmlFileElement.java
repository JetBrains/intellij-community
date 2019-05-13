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
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlElementType;
import org.jetbrains.annotations.NotNull;

public class HtmlFileElement extends FileElement implements XmlElementType  {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.HtmlFileElement");

  public HtmlFileElement(CharSequence text) {
    super(HTML_FILE, text);
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == HTML_DOCUMENT) {
      return XmlChildRole.HTML_DOCUMENT;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }
}
