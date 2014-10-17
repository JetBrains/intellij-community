/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.documentation.doctest;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.parsing.ExpressionParsing;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.StatementParsing;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.Nullable;

/**
 * User : ktisha
 */
public class PyDocstringParsingContext extends ParsingContext {
  private final StatementParsing myStatementParser;
  private final ExpressionParsing myExpressionParser;

  public PyDocstringParsingContext(final PsiBuilder builder,
                                   LanguageLevel languageLevel,
                                   StatementParsing.FUTURE futureFlag) {
    super(builder, languageLevel, futureFlag);
    myStatementParser = new PyDocstringStatementParsing(this, futureFlag);
    myExpressionParser = new PyDocstringExpressionParsing(this);
  }

  @Override
  public ExpressionParsing getExpressionParser() {
    return myExpressionParser;
  }

  @Override
  public StatementParsing getStatementParser() {
    return myStatementParser;
  }

  private static class PyDocstringExpressionParsing extends ExpressionParsing {
    public PyDocstringExpressionParsing(ParsingContext context) {
      super(context);
    }

    @Override
    protected IElementType getReferenceType() {
      return PyDocstringTokenTypes.DOC_REFERENCE;
    }
  }

  private static class PyDocstringStatementParsing extends StatementParsing {

    protected PyDocstringStatementParsing(ParsingContext context,
                                          @Nullable FUTURE futureFlag) {
      super(context, futureFlag);
    }

    @Override
    protected IElementType getReferenceType() {
      return PyDocstringTokenTypes.DOC_REFERENCE;
    }

    @Override
    public IElementType filter(IElementType source, int start, int end, CharSequence text) {
      if (source == PyTokenTypes.DOT && CharArrayUtil.regionMatches(text, start, end, "..."))
        return PyDocstringTokenTypes.DOTS;
      if (source == PyTokenTypes.GTGT && CharArrayUtil.regionMatches(text, start, end, ">>>"))
        return PyTokenTypes.SPACE;
      return super.filter(source, start, end, text);
    }
  }
}
