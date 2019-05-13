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
package com.intellij.embedding;

import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.ILazyParseableElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EmbeddedLazyParseableElementType extends ILazyParseableElementType implements EmbeddingElementType {

  public EmbeddedLazyParseableElementType(@NotNull @NonNls String debugName, @Nullable Language language) {
    super(debugName, language);
  }

  public Lexer createLexer(@NotNull ASTNode chameleon,
                              @NotNull Project project,
                              @NotNull Language parentLanguage) {
    final Language language = chameleon.getElementType().getLanguage();
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
    return parserDefinition.createLexer(project);
  }

  public ASTNode parseAndGetTree(@NotNull PsiBuilder builder) {
    final PsiParser parser = getParser(builder);
    return parser.parse(this, builder);
  }

  protected PsiParser getParser(@NotNull PsiBuilder builder) {
    return LanguageParserDefinitions.INSTANCE.forLanguage(getLanguage()).createParser(builder.getProject());
  }

  @Override
  protected ASTNode doParseContents(@NotNull ASTNode chameleon, @NotNull PsiElement psi) {
    PsiFile file = psi.getContainingFile();
    assert file != null : chameleon;

    final Project project = file.getProject();
    final Language language = chameleon.getElementType().getLanguage();
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
    final Lexer lexer = createLexer(chameleon, project, psi.getLanguage());

    final PsiBuilder builder = getBuilder(chameleon, project, parserDefinition, lexer);

    return parseAndGetTree(builder).getFirstChildNode();
  }

  public PsiBuilder getBuilder(ASTNode chameleon, Project project, ParserDefinition parserDefinition, Lexer lexer) {
    final PsiBuilder builder;
    if (lexer instanceof MasqueradingLexer) {
      builder =
        new MasqueradingPsiBuilderAdapter(project, parserDefinition, ((MasqueradingLexer)lexer), chameleon, chameleon.getChars());
    }
    else {
      builder = new PsiBuilderImpl(project, parserDefinition, lexer, chameleon, chameleon.getChars());
    }
    return builder;
  }
}
