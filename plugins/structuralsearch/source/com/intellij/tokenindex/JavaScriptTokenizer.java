package com.intellij.tokenindex;

import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.psi.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class JavaScriptTokenizer implements Tokenizer {
  private static final byte EXPRESSION_TYPE = 0;
  private static final byte IDENTIFIER_TYPE = 1;
  private static final byte PARAM_LIST_TYPE = 2;

  @NotNull
  public List<Token> tokenize(Collection<? extends PsiElement> roots) {
    ArrayList<Token> tokens = new ArrayList<Token>();
    for (PsiElement root : roots) {
      root.accept(new MyVisitor(tokens));
    }
    return tokens;
  }

  private static class MyVisitor extends JSRecursiveWalkingElementVisitor {
    private final List<Token> myTokens;

    private MyVisitor(List<Token> tokens) {
      myTokens = tokens;
    }

    @Override
    public void visitElement(PsiElement element) {
      super.visitElement(element);
      if (element instanceof LeafPsiElement && !(element instanceof PsiWhiteSpace) && !(element instanceof PsiComment)) {
        visitLeafElement((LeafPsiElement)element);
      }
    }

    @Override
    public void visitJSExpression(JSExpression expression) {
      TextRange range = expression.getTextRange();
      myTokens.add(new AnonymToken(EXPRESSION_TYPE, range.getStartOffset(), range.getEndOffset()));
    }

    @Override
    protected void elementFinished(@NotNull PsiElement element) {
      if (element instanceof JSStatement) {
        PsiElement parent = element.getParent();
        if (parent instanceof JSIfStatement || parent instanceof JSLoopStatement) {
          myTokens.add(new IndentToken(-1, element.getTextRange().getEndOffset()));
        }
      }
    }

    @Override
    public void visitJSParameterList(JSParameterList node) {
      TextRange range = node.getTextRange();
      myTokens.add(new AnonymToken(PARAM_LIST_TYPE, range.getStartOffset(), range.getEndOffset()));
    }

    private void visitLeafElement(LeafPsiElement element) {
      if (element.getTextLength() == 0) {
        return;
      }
      IElementType type = element.getElementType();
      if (type == JSTokenTypes.IDENTIFIER) {
        TextRange range = element.getTextRange();
        myTokens.add(new AnonymToken(IDENTIFIER_TYPE, range.getStartOffset(), range.getEndOffset()));
        return;
      }
      if (type == JSTokenTypes.LBRACE || type == JSTokenTypes.RBRACE) {
        PsiElement parent = element.getParent();
        if (parent instanceof JSBlockStatement) {
          parent = parent.getParent();
          if (parent instanceof JSIfStatement || parent instanceof JSLoopStatement) {
            return;
          }
        }
      }
      TextRange range = element.getTextRange();
      myTokens.add(new TextToken(element.getText().hashCode(), range.getStartOffset(), range.getEndOffset()));
    }
  }
}
