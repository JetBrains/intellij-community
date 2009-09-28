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
package org.intellij.lang.xpath.xslt.context;

import org.intellij.lang.xpath.context.ContextType;
import org.intellij.lang.xpath.context.functions.DefaultFunctionContext;
import org.intellij.lang.xpath.context.functions.Function;
import org.intellij.lang.xpath.context.functions.Parameter;
import org.intellij.lang.xpath.psi.XPathType;
import org.apache.commons.collections.map.CompositeMap;

import javax.xml.namespace.QName;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class XsltFunctionContext extends DefaultFunctionContext {

    protected static final Map<QName, Function> XSLT_FUNCTIONS;
    static {
        final Map<QName, Function> decls = new HashMap<QName, Function>();

        // string format-number(number, string, string?)
        addFunction(decls, "format-number", new Function(XPathType.STRING,
                new Parameter(XPathType.NUMBER, Parameter.Kind.REQUIRED),
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
                new Parameter(XPathType.STRING, Parameter.Kind.OPTIONAL)));

        // string unparsed-entity-uri(string)
        addFunction(decls, "unparsed-entity-uri", new Function(XPathType.STRING,
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)
        ));

        // node-set key(string, object)
        addFunction(decls, "key", new Function(XPathType.NODESET,
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
                new Parameter(XPathType.ANY, Parameter.Kind.REQUIRED)
        ));

        // string generate-id(node-set?)
        addFunction(decls, "generate-id", new Function(XPathType.STRING,
                new Parameter(XPathType.NODESET, Parameter.Kind.OPTIONAL)));

        // object system-property(string)
        addFunction(decls, "system-property", new Function(XPathType.ANY,
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)));

        // boolean element-available(string)
        addFunction(decls, "element-available", new Function(XPathType.BOOLEAN,
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)));

        // boolean function-available(string)
        addFunction(decls, "function-available", new Function(XPathType.BOOLEAN,
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)));

        // node-set current()
        addFunction(decls, "current", new Function(XPathType.NODESET));

        XSLT_FUNCTIONS = Collections.unmodifiableMap(decls);
    }

    public static final XsltFunctionContext INSTANCE = new XsltFunctionContext();
    
    public XsltFunctionContext() {
        super(XsltContextProvider.TYPE);
    }

    protected Map<QName, Function> getAdditionalFunctions(ContextType contextType) {
        return new CompositeMap(XSLT_FUNCTIONS, super.getAdditionalFunctions(contextType));
    }

    public boolean allowsExtensions() {
        return true;
    }
}
