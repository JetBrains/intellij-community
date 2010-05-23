package com.intellij.tokenindex;

import com.intellij.lang.ASTNode;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.psi.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.LeafElement;
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
    MyVisitor visitor = new MyVisitor();
    for (PsiElement root : roots) {
      root.accept(visitor);
    }
    return visitor.getTokens();
  }

  private static class MyVisitor extends JSRecursiveElementVisitor {
    private final List<Token> myTokens = new ArrayList<Token>();

    @Override
    public void visitElement(PsiElement element) {
      if (element instanceof JSExpression) {
        myTokens.add(new AnonymToken(EXPRESSION_TYPE, element.getTextOffset()));
      }
      else {
        super.visitElement(element);
        if (element instanceof PsiWhiteSpace || !(element instanceof LeafElement)) {
          return;
        }
        visitLeafElement(element);
      }
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
          statement.accept(this);
        }
      }
      else {
        super.visitJSBlock(node);
      }
    }

    private void visitLeafElement(PsiElement element) {
      if (element.getTextLength() == 0) {
        return;
      }
      ASTNode node = element.getNode();
      if (node != null && node.getElementType() == JSTokenTypes.IDENTIFIER) {
        myTokens.add(new AnonymToken(IDENTIFIER_TYPE, element.getTextOffset()));
      }
      else {
        myTokens.add(new TextToken(element.getText().hashCode(), element.getTextOffset()));
      }
    }

    public List<Token> getTokens() {
      return myTokens;
    }
  }
}
