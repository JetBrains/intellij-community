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
package org.intellij.plugins.xpathView.support.jaxen.extensions;

import org.intellij.lang.xpath.context.ContextType;
import org.intellij.lang.xpath.context.functions.Function;
import org.intellij.lang.xpath.context.functions.Parameter;
import org.intellij.lang.xpath.context.functions.XPathFunctionProvider;
import org.intellij.lang.xpath.psi.XPathType;
import org.intellij.plugins.xpathView.support.XPathSupport;
import org.jaxen.function.ext.EndsWithFunction;
import org.jaxen.function.ext.EvaluateFunction;
import org.jaxen.function.ext.LowerFunction;
import org.jaxen.function.ext.UpperFunction;
import org.jetbrains.annotations.NotNull;

import javax.xml.namespace.QName;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple implementation that adds extension functions for file-type, file-name and file-extension
 */
class XPathFunctionProviderImpl extends XPathFunctionProvider {
    private final Map<QName, FunctionImplementation> myFunctions = new HashMap<>();

    public XPathFunctionProviderImpl() {
        // XPathView extensions
        myFunctions.put(new QName(null, "file-type"), new FileTypeFunction());
        myFunctions.put(new QName(null, "file-name"), new FileNameFunction());
        myFunctions.put(new QName(null, "file-ext"), new FileExtensionFunction());

        // Extensions provided by Jaxen
        //myFunctions.put(new QName(null, "matrix-concat"), new JaxenMatrixConcat());
        myFunctions.put(new QName(null, "evaluate"), new JaxenEvaluate());
        myFunctions.put(new QName(null, "lower-case"), new JaxenLower());
        myFunctions.put(new QName(null, "upper-case"), new JaxenUpper());
        myFunctions.put(new QName(null, "ends-with"), new JaxenEndsWith());
    }

    @NotNull
    public Map<QName, ? extends Function> getFunctions(ContextType contextType) {
        if (contextType == XPathSupport.TYPE) {
            return myFunctions;
        } else {
            //noinspection unchecked
            return Collections.emptyMap();
        }
    }

    private static class JaxenEvaluate extends FunctionImplementation {
        public JaxenEvaluate() {
            super("evaluate", XPathType.NODESET,
                    new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED));
        }

        public org.jaxen.Function getImplementation() {
            return new EvaluateFunction();
        }
    }

    private static class JaxenLower extends FunctionImplementation {
        public JaxenLower() {
            super("lower-case", XPathType.STRING,
                    new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
                    new Parameter(XPathType.STRING, Parameter.Kind.OPTIONAL));
        }

        public org.jaxen.Function getImplementation() {
            return new LowerFunction();
        }
    }

    private static class JaxenUpper extends FunctionImplementation {
        public JaxenUpper() {
            super("upper-case", XPathType.STRING,
                    new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
                    new Parameter(XPathType.STRING, Parameter.Kind.OPTIONAL));
        }

        public org.jaxen.Function getImplementation() {
            return new UpperFunction();
        }
    }

    private static class JaxenEndsWith extends FunctionImplementation {
        public JaxenEndsWith() {
            super("ends-with", XPathType.BOOLEAN,
                    new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
                    new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED));
        }

        public org.jaxen.Function getImplementation() {
            return new EndsWithFunction();
        }
    }
}
