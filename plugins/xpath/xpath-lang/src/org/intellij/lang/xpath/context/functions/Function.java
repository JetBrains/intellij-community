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

import java.util.Arrays;

public class Function {
    public final Parameter[] parameters;
    public final XPathType returnType;
    public final int minArity;

    public Function(XPathType returnType) {
        this(returnType, new Parameter[0]);
    }

    public Function(XPathType returnType, Parameter... parameters) {
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

    public String buildSignature(String name) {
        final StringBuilder sb = new StringBuilder(name).append("(");
        sb.append(StringUtil.join(Arrays.asList(parameters), StringUtil.createToStringFunction(Parameter.class), ", "));
        return sb.append(")").toString();
    }
}
