// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.impl.source.parsing.xml;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.html.HtmlParsing;
import org.jetbrains.annotations.NotNull;

public class HtmlBuilderDriver extends XmlBuilderDriver {
  public HtmlBuilderDriver(final CharSequence text) {
    super(text);
  }

  @Override
  protected @NotNull PsiBuilder createBuilderAndParse() {
    final ParserDefinition htmlParserDef = LanguageParserDefinitions.INSTANCE.forLanguage(HTMLLanguage.INSTANCE);
    assert htmlParserDef != null;

    PsiBuilder b = PsiBuilderFactory.getInstance().createBuilder(htmlParserDef, htmlParserDef.createLexer(null), getText());
    new HtmlParsing(b).parseDocument();
    return b;
  }
}
