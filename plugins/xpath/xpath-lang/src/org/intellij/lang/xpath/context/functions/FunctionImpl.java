/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.context.functions;

import com.intellij.openapi.util.text.StringUtil;

import org.intellij.lang.xpath.psi.XPathType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class FunctionImpl implements Function {
  private final String name;
  private final Parameter[] parameters;
  private final XPathType returnType;
  private final int minArity;

  public FunctionImpl(String name, @NotNull XPathType returnType, Parameter... parameters) {
    this.name = name;
    this.parameters = parameters;
    this.returnType = returnType;
    this.minArity = calcArity(parameters);
  }

  private static int calcArity(Parameter[] parameters) {
    int arity = 0;
    boolean stop = false;
    for (Parameter parameter : parameters) {
      assert !stop;

      if (parameter.kind == Parameter.Kind.REQUIRED) {
        arity++;
      } else if (parameter.kind == Parameter.Kind.OPTIONAL) {
        stop = true;
      } else if (parameter.kind == Parameter.Kind.VARARG) {
        stop = true;
      }
    }
    return arity;
  }

  @Override
  public String buildSignature() {
    final StringBuilder sb = new StringBuilder(getName()).append("(");
    sb.append(StringUtil.join(Arrays.asList(parameters), StringUtil.createToStringFunction(Parameter.class), ", "));
    return sb.append(")").toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final FunctionImpl function = (FunctionImpl)o;

    if (!name.equals(function.name)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String getName() {
    return name;
  }

  @NotNull
  @Override
  public Parameter[] getParameters() {
    return parameters;
  }

  @NotNull
  @Override
  public XPathType getReturnType() {
    return returnType;
  }

  @Override
  public int getMinArity() {
    return minArity;
  }
}
