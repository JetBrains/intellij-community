/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 11:49:33
 * To change this template use File | Settings | File Templates.
 */
public class Parsing {
    private ParsingContext context;

    protected Parsing(ParsingContext context) {
        this.context = context;
    }

    public ParsingContext getParsingContext() {
        return context;
    }

    public ExpressionParsing getExpressionParser() { return getParsingContext().getExpressionParser(); }
    public StatementParsing getStatementParser() { return getParsingContext().getStatementParser(); }
    public FunctionParsing getFunctionParser() { return getParsingContext().getFunctionParser(); }

    protected static void checkMatches(final PsiBuilder builder, final IElementType token, final String message) {
        if (builder.getTokenType() == token) {
            builder.advanceLexer();
        }
        else {
            builder.error(message);
        }
    }

    protected static void checkMatches(final PsiBuilder builder, final TokenSet tokenSet, final String message) {
        if (tokenSet.contains(builder.getTokenType())) {
            builder.advanceLexer();
        }
        else {
            builder.error(message);
        }
    }
}
