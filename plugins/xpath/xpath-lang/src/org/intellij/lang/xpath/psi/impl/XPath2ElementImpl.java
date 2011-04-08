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
package org.intellij.lang.xpath.psi.impl;

import com.intellij.lang.ASTNode;
import org.intellij.lang.xpath.psi.XPath2ElementVisitor;
import org.intellij.lang.xpath.psi.XPathElementVisitor;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 08.04.11
*/
public class XPath2ElementImpl extends XPathElementImpl {
  public XPath2ElementImpl(ASTNode node) {
    super(node);
  }

  public void accept(XPath2ElementVisitor visitor) {
    visitor.visitXPath2Element(this);
  }

  @Override
  public final void accept(XPathElementVisitor visitor) {
    if (visitor instanceof XPath2ElementVisitor) {
      accept((XPath2ElementVisitor)visitor);
    } else {
      super.accept(visitor);
    }
  }
}