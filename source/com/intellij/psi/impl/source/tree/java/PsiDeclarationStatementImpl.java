package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public class PsiDeclarationStatementImpl extends CompositePsiElement implements PsiDeclarationStatement {
  public PsiDeclarationStatementImpl() {
    super(DECLARATION_STATEMENT);
  }

  public PsiElement[] getDeclaredElements() {
    return getChildrenAsPsiElements(DECLARED_ELEMENT_BIT_SET, PSI_ELEMENT_ARRAY_CONSTRUCTOR);
  }

  private static final TokenSet DECLARED_ELEMENT_BIT_SET = TokenSet.create(new IElementType[]{LOCAL_VARIABLE, CLASS});

  public int getChildRole(TreeElement child) {
    if (child.getElementType() == ElementType.COMMA) return ChildRole.COMMA;
    return super.getChildRole(child);
  }

  public void deleteChildInternal(TreeElement child) {
    if (DECLARED_ELEMENT_BIT_SET.isInSet(child.getElementType())) {
      PsiElement[] declaredElements = getDeclaredElements();
      int length = declaredElements.length;
      if (length > 0) {
        if (length == 1) {
          getTreeParent().deleteChildInternal(this);
          return;
        } else {
          if (SourceTreeToPsiMap.psiElementToTree(declaredElements[length - 1]) == child) {
            removeCommaBefore(child);
            final LeafElement semicolon = Factory.createSingleLeafElement(SEMICOLON, new char[]{';'}, 0, 1,
                                                                          SharedImplUtil.findCharTableByTree(this), getManager());
            ChangeUtil.addChild((CompositeElement)SourceTreeToPsiMap.psiElementToTree(declaredElements[length - 2]), semicolon, null);
          }
          else if (SourceTreeToPsiMap.psiElementToTree(declaredElements[0]) == child) {
            CompositeElement next = (CompositeElement)SourceTreeToPsiMap.psiElementToTree(declaredElements[1]);
            TreeElement copyChild = child.copyElement();
            TreeElement nameChild = ((CompositeElement)copyChild).findChildByRole(ChildRole.NAME);
            removeCommaBefore(next);
            next.addInternal(((CompositeElement)copyChild).firstChild, nameChild.getTreePrev(), null, Boolean.FALSE);
          }
          else {
            removeCommaBefore (child);
          }
        }
      }
    }
    super.deleteChildInternal(child);
  }

  private void removeCommaBefore(TreeElement child) {
    TreeElement prev = child;
    do {
      prev = prev.getTreePrev();
    } while (prev != null && JavaTokenType.WHITE_SPACE_OR_COMMENT_BIT_SET.isInSet(prev.getElementType()));
    if (prev != null && prev.getElementType() == COMMA) deleteChildInternal(prev);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitDeclarationStatement(this);
  }

  public String toString() {
    return "PsiDeclarationStatement";
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    PsiElement[] decls = getDeclaredElements();
    for (int i = 0; i < decls.length; i++) {
      if (decls[i] != lastParent) {
        if (!processor.execute(decls[i], substitutor)) return false;
      }
      else {
        final ElementClassHint hint = processor.getHint(ElementClassHint.class);
        if (lastParent instanceof PsiClass) {
          if (hint == null || hint.shouldProcess(PsiClass.class)) {
            processor.execute(lastParent, substitutor);
          }
        }
      }
    }

    return true;
  }
}
