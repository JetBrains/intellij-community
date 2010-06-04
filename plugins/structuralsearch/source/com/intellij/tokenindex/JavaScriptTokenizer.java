package com.intellij.tokenindex;

import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.psi.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
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
      tokenize(root, tokens);
    }
    return tokens;
  }

  private static void tokenize(PsiElement root, List<Token> tokens) {
    root.accept(new MyVisitor(tokens));
  }

  private static class MyVisitor extends JSRecursiveWalkingElementVisitor {
    private final List<Token> myTokens;

    private MyVisitor(List<Token> tokens) {
      myTokens = tokens;
    }

    @Override
    public void visitElement(PsiElement element) {
      super.visitElement(element);
      if (element instanceof LeafPsiElement && !(element instanceof PsiWhiteSpace)) {
        visitLeafElement((LeafPsiElement)element);
      }
    }

    @Override
    public void visitJSExpression(JSExpression expression) {
      int offset = expression.getTextOffset();
      myTokens.add(new AnonymToken(EXPRESSION_TYPE, offset));
    }

    @Override
    public void visitJSParameterList(JSParameterList node) {
      myTokens.add(new AnonymToken(PARAM_LIST_TYPE, node.getTextOffset()));
    }

    @Override
    public void visitJSBlock(JSBlockStatement node) {
      PsiElement parent = node.getParent();
      if (parent instanceof JSIfStatement || parent instanceof JSLoopStatement) {
        for (JSStatement statement : node.getStatements()) {
          tokenize(statement, myTokens);
        }
      }
      else {
        super.visitElement(node);
      }
    }

    private void visitLeafElement(LeafPsiElement element) {
      if (element.getTextLength() == 0) {
        return;
      }
      if (element.getElementType() == JSTokenTypes.IDENTIFIER) {
        myTokens.add(new AnonymToken(IDENTIFIER_TYPE, element.getTextOffset()));
      }
      else {
        myTokens.add(new TextToken(element.getText().hashCode(), element.getTextOffset()));
      }
    }
  }
}
