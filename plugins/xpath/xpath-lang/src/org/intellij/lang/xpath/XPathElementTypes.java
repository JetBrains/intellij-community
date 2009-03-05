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
package org.intellij.lang.xpath;

import com.intellij.lang.Language;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;

public final class XPathElementTypes {
    public static final IElementType EMBEDDED_CONTENT = new IElementType("EMBEDDED_CONTENT", Language.findInstance(XPathLanguage.class));

    public static final IFileElementType FILE = new IFileElementType("XPATH_FILE", XPathFileType.XPATH.getLanguage());

    public static final IElementType BINARY_EXPRESSION = new XPathElementType("BINARY_EXPRESSION");
    public static final IElementType PREFIX_EXPRESSION = new XPathElementType("PREFIX_EXPRESSION");
    public static final IElementType PATH_EXPRESSION = new XPathElementType("PATH_EXPRESSION");
    public static final IElementType FILTER_EXPRESSION = new XPathElementType("FILTER_EXPRESSION");
    public static final IElementType PREDICATE = new XPathElementType("PREDICATE");
    public static final IElementType NUMBER = new XPathElementType("NUMBER");
    public static final IElementType STRING = new XPathElementType("STRING");
    public static final IElementType VARIABLE_REFERENCE = new XPathElementType("VARIABLE_REFERENCE");
    public static final IElementType PARENTHESIZED_EXPR = new XPathElementType("PARENTHESIZED_EXPR");
    public static final IElementType FUNCTION_CALL = new XPathElementType("FUNCTION_CALL");
    public static final IElementType AXIS_SPECIFIER = new XPathElementType("AXIS_SPECIFIER");

    public static final IElementType LOCATION_PATH = new XPathElementType("LOCATION_PATH");

    public static final IElementType NODE_TEST = new XPathElementType("NODE_TEST");
    public static final IElementType NODE_TYPE = new XPathElementType("NODE_TYPE");
    public static final IElementType STEP = new XPathElementType("STEP");

    public static final TokenSet EXPRESSIONS = TokenSet.create(
            NUMBER, STRING, 
            FUNCTION_CALL, VARIABLE_REFERENCE,
            BINARY_EXPRESSION, PREFIX_EXPRESSION, PARENTHESIZED_EXPR,
            PATH_EXPRESSION, FILTER_EXPRESSION, LOCATION_PATH
    );

    public static final TokenSet PREDICATES = TokenSet.create(PREDICATE);
    public static final TokenSet STEPS = TokenSet.orSet(EXPRESSIONS, TokenSet.create(STEP));

    private XPathElementTypes() {
    }
}
