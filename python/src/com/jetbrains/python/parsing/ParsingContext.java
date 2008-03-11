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

public class ParsingContext {
  private StatementParsing stmtParser;
  private ExpressionParsing expressionParser;
  private FunctionParsing functionParser;
  private PsiBuilder myBuilder;

  public ParsingContext(final PsiBuilder builder) {
    myBuilder = builder;
    stmtParser = new StatementParsing(this);
    expressionParser = new ExpressionParsing(this);
    functionParser = new FunctionParsing(this);
  }

  public StatementParsing getStatementParser() {
    return stmtParser;
  }

  public ExpressionParsing getExpressionParser() {
    return expressionParser;
  }

  public FunctionParsing getFunctionParser() {
    return functionParser;
  }

  public PsiBuilder getBuilder() {
    return myBuilder;
  }
}
