/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.fileTypes;

import com.intellij.lang.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.PsiPlainTextFileImpl;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.util.PsiUtil;
import com.intellij.lexer.EmptyLexer;
import com.intellij.lexer.Lexer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 15, 2006
 * Time: 7:42:03 PM
 * To change this template use File | Settings | File Templates.
 */
public class PlainTextLanguage extends Language {
  private ParserDefinition myParserDefinition;

  protected PlainTextLanguage() {
    super("TEXT", "text/plain");
  }

  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(Project project, final VirtualFile virtualFile) {
    return new PlainSyntaxHighlighter();
  }

  @Nullable
  public ParserDefinition getParserDefinition() {
    if (myParserDefinition == null) {
      myParserDefinition = new ParserDefinition() {

        @NotNull
        public Lexer createLexer(Project project) {
          return new EmptyLexer();
        }

        @NotNull
        public PsiParser createParser(Project project) {
          throw new UnsupportedOperationException("Not supported");
        }

        public IFileElementType getFileNodeType() {
          return new IFileElementType(PlainTextLanguage.this) {
            public ASTNode parseContents(ASTNode chameleon) {
              final char[] chars = ((LeafElement)chameleon).textToCharArray();
              return Factory.createLeafElement(ElementType.PLAIN_TEXT, chars,0,chars.length,0, SharedImplUtil.findCharTableByTree(chameleon));
            }
          };
        }

        @NotNull
        public TokenSet getWhitespaceTokens() {
          return TokenSet.EMPTY;
        }

        @NotNull
        public TokenSet getCommentTokens() {
          return StdLanguages.HTML.getParserDefinition().getCommentTokens();  // HACK!
        }

        @NotNull
        public PsiElement createElement(ASTNode node) {
          return PsiUtil.NULL_PSI_ELEMENT;
        }

        public PsiFile createFile(FileViewProvider viewProvider) {
          return new PsiPlainTextFileImpl(viewProvider);
        }

        public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
          return SpaceRequirements.MAY;
        }
      };
    }
    return myParserDefinition;
  }
}
