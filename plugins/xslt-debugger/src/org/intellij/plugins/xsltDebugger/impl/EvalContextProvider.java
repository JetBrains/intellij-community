/*
 * Copyright 2007 Sascha Weinreuter
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

package org.intellij.plugins.xsltDebugger.impl;

import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtilRt;
import org.intellij.lang.xpath.context.*;
import org.intellij.lang.xpath.psi.XPathElement;
import org.intellij.lang.xpath.xslt.context.XsltContextProvider;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class EvalContextProvider extends ContextProvider {
  private final List<? extends Debugger.Variable> myVariables;

  public EvalContextProvider(List<? extends Debugger.Variable> model) {
    myVariables = model;
  }

  @Override
  public @NotNull ContextType getContextType() {
    return XsltContextProvider.TYPE;
  }

  @Override
  public @Nullable XmlElement getContextElement() {
    return null;
  }

  @Override
  protected boolean isValid() {
    return true;
  }

  @Override
  public @Nullable NamespaceContext getNamespaceContext() {
    return null;
  }

  @Override
  public VariableContext getVariableContext() {
    return new SimpleVariableContext() {
      @Override
      public String @NotNull [] getVariablesInScope(XPathElement element) {
        final int size = myVariables.size();
        final ArrayList<String> vars = new ArrayList<>(size);
        for (Debugger.Variable myVariable : myVariables) {
          vars.add(myVariable.getName());
        }
        return ArrayUtilRt.toStringArray(vars);
      }
    };
  }

  @Override
  public @Nullable Set<QName> getAttributes(boolean forValidation) {
    return null; // TODO
  }

  @Override
  public @Nullable Set<QName> getElements(boolean forValidation) {
    return null; // TODO
  }
}
