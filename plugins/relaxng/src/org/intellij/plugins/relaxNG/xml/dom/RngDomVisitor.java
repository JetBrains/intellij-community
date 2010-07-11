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

package org.intellij.plugins.relaxNG.xml.dom;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 18.08.2007
 */
public class RngDomVisitor implements DomElementVisitor {
  public void visitDomElement(DomElement element) {
  }

  public void visit(RngGrammar grammar) {
    visitDomElement(grammar);
  }

  public void visit(RngInclude include) {
    visitDomElement(include);
  }

  public void visit(RngDiv div) {
    visitDomElement(div);
  }

  public void visit(RngDefine def) {
    visitDomElement(def);
  }
}
