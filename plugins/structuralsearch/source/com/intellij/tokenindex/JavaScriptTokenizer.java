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

/**
 * @author Eugene.Kudelevsky
 */
public class JavaScriptTokenizer implements Tokenizer {
  private static final byte EXPRESSION_TYPE = 0;
  private static final byte IDENTIFIER_TYPE = 1;
  private static final byte PARAM_LIST_TYPE = 2;

  private final MyVisitor myVisitor = new MyVisitor();

  public boolean visit(@NotNull PsiElement node, RecursiveTokenizingVisitor globalVisitor) {
    myVisitor.myGlobalVisitor = globalVisitor;
    myVisitor.myToVisitChildren = false;
    node.accept(myVisitor);
    return myVisitor.myToVisitChildren;
  }

  public void elementFinished(@NotNull PsiElement element, RecursiveTokenizingVisitor globalVisitor) {
    if (element instanceof JSStatement) {
      PsiElement parent = element.getParent();
      if (parent instanceof JSIfStatement || parent instanceof JSLoopStatement) {
        globalVisitor.addToken(new IndentToken(-1, globalVisitor.getBaseOffset() + element.getTextRange().getEndOffset()));
      }
    }
  }

  private static class MyVisitor extends JSElementVisitor {
    private RecursiveTokenizingVisitor myGlobalVisitor;
    private boolean myToVisitChildren;

    @Override
    public void visitElement(PsiElement element) {
      myToVisitChildren = true;
      if (element instanceof LeafPsiElement && !(element instanceof PsiWhiteSpace) && !(element instanceof PsiComment)) {
        visitLeafElement((LeafPsiElement)element);
      }
    }

    @Override
    public void visitJSExpression(JSExpression expression) {
      TextRange range = expression.getTextRange();
      myGlobalVisitor.getTokens().add(new AnonymToken(EXPRESSION_TYPE, myGlobalVisitor.getBaseOffset() + range.getStartOffset(),
                                                      myGlobalVisitor.getBaseOffset() + range.getEndOffset()));
    }

    @Override
    public void visitJSParameterList(JSParameterList node) {
      TextRange range = node.getTextRange();
      myGlobalVisitor.addToken(new AnonymToken(PARAM_LIST_TYPE, myGlobalVisitor.getBaseOffset() + range.getStartOffset(),
                                               myGlobalVisitor.getBaseOffset() + range.getEndOffset()));
    }

    private void visitLeafElement(LeafPsiElement element) {
      if (element.getTextLength() == 0) {
        return;
      }
      IElementType type = element.getElementType();
      if (type == JSTokenTypes.IDENTIFIER) {
        TextRange range = element.getTextRange();
        myGlobalVisitor.addToken(new AnonymToken(IDENTIFIER_TYPE, myGlobalVisitor.getBaseOffset() + range.getStartOffset(),
                                                 myGlobalVisitor.getBaseOffset() + range.getEndOffset()));
        return;
      }
      if (type == JSTokenTypes.LBRACE || type == JSTokenTypes.RBRACE) {
        PsiElement parent = element.getParent();
        if (parent instanceof JSBlockStatement) {
          JSStatement[] statements = ((JSBlockStatement)parent).getStatements();
          if (statements != null && statements.length == 1) {
            parent = parent.getParent();
            if (parent instanceof JSIfStatement || parent instanceof JSLoopStatement) {
              return;
            }
          }
        }
      }
      TextRange range = element.getTextRange();
      myGlobalVisitor.addToken(new TextToken(element.getText().hashCode(), myGlobalVisitor.getBaseOffset() + range.getStartOffset(),
                                             myGlobalVisitor.getBaseOffset() + range.getEndOffset()));
    }
  }
}
