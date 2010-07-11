/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.xml.dom.impl;

import org.intellij.plugins.relaxNG.model.CommonElement;
import org.intellij.plugins.relaxNG.model.Div;
import org.intellij.plugins.relaxNG.model.Pattern;
import org.intellij.plugins.relaxNG.xml.dom.RngDomElement;

import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.DomUtil;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 31.08.2007
 */
public abstract class RngDomElementBase implements RngDomElement, Pattern<XmlElement> {
  public XmlElement getPsiElement() {
    return getXmlElement();
  }

  public void accept(Visitor visitor) {
    if (this instanceof Div) {
      visitor.visitDiv((Div)this); // TODO fix me
    } else {
      visitor.visitElement(this);
    }
  }

  public void acceptChildren(final Visitor visitor) {
    acceptChildren(new DomElementVisitor() {
      public void visitDomElement(DomElement element) {
        if (element instanceof CommonElement) {
          ((CommonElement)element).accept(visitor);
        }
      }
    });
  }

  protected static Pattern getPatternFrom(RngDomElement t) {
    if (t == null) return null;
    final List<Pattern> list = DomUtil.getChildrenOfType(t, Pattern.class);
    return list.size() > 0 ? list.get(0) : null;
  }
}
