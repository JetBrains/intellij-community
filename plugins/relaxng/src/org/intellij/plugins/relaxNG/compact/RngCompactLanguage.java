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
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.relaxNG.compact.psi.RncDefine;
import org.intellij.plugins.relaxNG.compact.psi.RncElement;
import org.intellij.plugins.relaxNG.compact.psi.util.EscapeUtil;
import org.intellij.plugins.relaxNG.compact.psi.util.RenameUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 01.08.2007
*/
public class RngCompactLanguage extends Language {
  public static final String ID = "RELAX-NG";

  public static final RngCompactLanguage INSTANCE = new RngCompactLanguage();

  private RngCompactLanguage() {
    super(ID);
  }

  public static class MyCommenter implements Commenter {
    @Nullable
    public String getLineCommentPrefix() {
      return "#";
    }

    @Nullable
    public String getBlockCommentPrefix() {
      return null;
    }

    @Nullable
    public String getBlockCommentSuffix() {
      return null;
    }

    public String getCommentedBlockCommentPrefix() {
      return null;
    }

    public String getCommentedBlockCommentSuffix() {
      return null;
    }
  }

  public static class MyPairedBraceMatcher implements PairedBraceMatcher {
    private BracePair[] myBracePairs;

    public BracePair[] getPairs() {
      if (myBracePairs == null) {
        myBracePairs = new BracePair[]{
                new BracePair(RncTokenTypes.LBRACE, RncTokenTypes.RBRACE, true),
                new BracePair(RncTokenTypes.LPAREN, RncTokenTypes.RPAREN, false),
                new BracePair(RncTokenTypes.LBRACKET, RncTokenTypes.RBRACKET, false),
        };
      }
      return myBracePairs;
    }

    public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
      return false;
    }

    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
      // TODO
      return openingBraceOffset;
    }
  }

  public static class MyNamesValidator implements NamesValidator {
    public boolean isKeyword(String name, Project project) {
      return RenameUtil.isKeyword(PsiManager.getInstance(project), name);
    }

    public boolean isIdentifier(String name, Project project) {
      return RenameUtil.isIdentifier(PsiManager.getInstance(project), name);
    }
  }

  public static class MyDocumentationProvider implements DocumentationProvider {
    @Nullable
    public String getQuickNavigateInfo(PsiElement element) {
      return null;
    }

    public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
      return null;
    }

    @Nullable
    public String generateDoc(PsiElement element, PsiElement originalElement) {
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

    @Nullable
    public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
      return null;
    }

    @Nullable
    public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
      return null;
    }
  }

  public static class MySyntaxHighlighterFactory extends SingleLazyInstanceSyntaxHighlighterFactory {
    @NotNull
    protected SyntaxHighlighter createHighlighter() {
      return new RncHighlighter();
    }
  }
}
