// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.intellij.plugins.xpathView.util;

import com.intellij.psi.xml.XmlElement;
import org.jaxen.UnresolvableException;
import org.jaxen.VariableContext;
import org.jaxen.XPath;
import org.jaxen.saxpath.SAXPathException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CachedVariableContext implements VariableContext {
    private final Map<String, Object> myMap = new HashMap<>();

    public CachedVariableContext(Collection<Variable> variables, XPath xpath, XmlElement contextNode) throws SAXPathException {
       for (Variable variable : variables) {
           if (variable.getName().isEmpty()) {
               continue;
           }
           final String expression = variable.getExpression();
           // empty expression evaluates to empty nodeset
           final XPath xPath = xpath.getNavigator().parseXPath(expression.isEmpty() ? "/.." : expression);
           myMap.put(variable.getName(), xPath.evaluate(contextNode));
       }
   }

   @Override
   public Object getVariableValue(String nsURI, String prefix, String localName) throws UnresolvableException {
       final Object o = myMap.get(localName);
       if (o == null) throw new UnresolvableException("Unresolved variable: " + makePrefix(nsURI) + localName);
       return o;
   }

   private static String makePrefix(String uri) {
       return uri != null ? "{" + uri + "}:" : "";
   }
}