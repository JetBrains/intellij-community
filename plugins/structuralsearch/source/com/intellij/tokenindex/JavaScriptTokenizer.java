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
    MyVisitor visitor = new MyVisitor();
    for (PsiElement root : roots) {
      root.accept(visitor);
    }
    return visitor.getTokens();
  }

  private static class MyVisitor extends JSRecursiveWalkingElementVisitor {
    private final List<Token> myTokens = new ArrayList<Token>();
    //private int myParentOffset = -1;

    @Override
    public void visitElement(PsiElement element) {
      visitChildren(element);
      if (element instanceof LeafPsiElement && !(element instanceof PsiWhiteSpace)) {
        visitLeafElement((LeafPsiElement)element);
      }
    }

    private void visitChildren(PsiElement element) {
      //int temp = myParentOffset;
      //myParentOffset = getTextOffset(element);
      super.visitElement(element);
      //myParentOffset = temp;
    }

    @Override
    public void visitJSExpression(JSExpression expression) {
      int offset = getTextOffset(expression);
      myTokens.add(new AnonymToken(EXPRESSION_TYPE, offset));
    }

    private int getTextOffset(@NotNull PsiElement element) {
      // performance hint: TreeElement#getTextOffset() works recursively upwards
      /*if (myParentOffset >= 0) {
        ASTNode node = element.getNode();
        if (node instanceof TreeElement) {
          return myParentOffset + ((TreeElement)node).getStartOffsetInParent();
        }
      }*/
      return element.getTextOffset();
    }

    @Override
    public void visitJSParameterList(JSParameterList node) {
      myTokens.add(new AnonymToken(PARAM_LIST_TYPE, getTextOffset(node)));
    }

    @Override
    public void visitJSBlock(JSBlockStatement node) {
      PsiElement parent = node.getParent();
      if (parent instanceof JSIfStatement || parent instanceof JSLoopStatement) {
        //int temp = myParentOffset;
        //myParentOffset = getTextOffset(node);
        for (JSStatement statement : node.getStatements()) {
          statement.accept(this);
        }
        //myParentOffset = temp;
      }
      else {
        visitChildren(node);
      }
    }

    private void visitLeafElement(LeafPsiElement element) {
      if (element.getTextLength() == 0) {
        return;
      }
      if (element.getElementType() == JSTokenTypes.IDENTIFIER) {
        myTokens.add(new AnonymToken(IDENTIFIER_TYPE, getTextOffset(element)));
      }
      else {
        myTokens.add(new TextToken(element.getText().hashCode(), getTextOffset(element)));
      }
    }

    public List<Token> getTokens() {
      return myTokens;
    }
  }
}
