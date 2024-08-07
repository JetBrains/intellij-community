/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.compact;

import com.intellij.lang.BracePair;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.relaxNG.compact.psi.RncDefine;
import org.intellij.plugins.relaxNG.compact.psi.RncElement;
import org.intellij.plugins.relaxNG.compact.psi.util.EscapeUtil;
import org.intellij.plugins.relaxNG.compact.psi.util.RenameUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RngCompactLanguage extends Language {
  public static final String ID = "RELAX-NG";

  public static final RngCompactLanguage INSTANCE = new RngCompactLanguage();

  private RngCompactLanguage() {
    super(ID, "application/relax-ng-compact-syntax");
  }

  public static class MyCommenter implements Commenter {
    @Override
    public @Nullable String getLineCommentPrefix() {
      return "#";
    }

    @Override
    public @Nullable String getBlockCommentPrefix() {
      return null;
    }

    @Override
    public @Nullable String getBlockCommentSuffix() {
      return null;
    }

    @Override
    public String getCommentedBlockCommentPrefix() {
      return null;
    }

    @Override
    public String getCommentedBlockCommentSuffix() {
      return null;
    }
  }

  public static class MyPairedBraceMatcher implements PairedBraceMatcher {
    private BracePair[] myBracePairs;

    @Override
    public BracePair @NotNull [] getPairs() {
      if (myBracePairs == null) {
        myBracePairs = new BracePair[]{
                new BracePair(RncTokenTypes.LBRACE, RncTokenTypes.RBRACE, true),
                new BracePair(RncTokenTypes.LPAREN, RncTokenTypes.RPAREN, false),
                new BracePair(RncTokenTypes.LBRACKET, RncTokenTypes.RBRACKET, false),
        };
      }
      return myBracePairs;
    }

    @Override
    public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
      return false;
    }

    @Override
    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
      // TODO
      return openingBraceOffset;
    }
  }

  public static class MyNamesValidator implements NamesValidator {
    @Override
    public boolean isKeyword(@NotNull String name, Project project) {
      return RenameUtil.isKeyword(name);
    }

    @Override
    public boolean isIdentifier(@NotNull String name, Project project) {
      return RenameUtil.isIdentifier(name);
    }
  }

  public static class MyDocumentationProvider implements DocumentationProvider {

    @Override
    public @Nullable @Nls String generateDoc(PsiElement element, PsiElement originalElement) {
      if (element instanceof RncElement) {
        PsiElement comment = element.getPrevSibling();
        while (comment instanceof PsiWhiteSpace) {
          comment = comment.getPrevSibling();
        }
        if (comment instanceof PsiComment) {
          final StringBuilder sb = new StringBuilder();
          do {
            sb.insert(0, EscapeUtil.unescapeText(comment).replaceAll("\n?##?", "") + "<br>");
            comment = comment.getPrevSibling();
          } while (comment instanceof PsiComment);

          if (element instanceof RncDefine) {
            sb.insert(0, "Define: <b>" + ((RncDefine)element).getName() + "</b><br>");
          }

          return sb.toString();
        }
      }
      return null;
    }
  }
}
