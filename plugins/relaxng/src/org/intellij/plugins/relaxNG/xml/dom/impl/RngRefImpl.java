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

import org.intellij.plugins.relaxNG.model.Define;
import org.intellij.plugins.relaxNG.model.resolve.DefinitionResolver;
import org.intellij.plugins.relaxNG.xml.dom.RngGrammar;
import org.intellij.plugins.relaxNG.xml.dom.RngRef;

import com.intellij.psi.xml.XmlAttributeValue;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 05.09.2007
 */
public abstract class RngRefImpl extends RngDomElementBase implements RngRef {
  @Override
  public void accept(Visitor visitor) {
    visitor.visitRef(this);
  }

  public Define getPattern() {
    final XmlAttributeValue value = getName().getXmlAttributeValue();
    if (value == null) return null;

    final String name = getReferencedName();
    if (name == null) {
      return null;
    }

    final RngGrammar scope = getScope();
    if (scope == null) {
      return null;
    }

    final Set<Define> defines = DefinitionResolver.resolve(scope, name);

    // TODO: honor combine & return virtual element if defines.size() > 1
    return defines != null && defines.size() > 0 ? defines.iterator().next() : null;
  }

  protected RngGrammar getScope() {
    return getParentOfType(RngGrammar.class, true);
  }

  public String getReferencedName() {
    return getName().getValue();
  }
}
