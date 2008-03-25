package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class PsiCatchSectionImpl extends CompositePsiElement implements PsiCatchSection, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiCatchSectionImpl");

  public PsiCatchSectionImpl() {
    super(CATCH_SECTION);
  }

  public PsiParameter getParameter() {
    return (PsiParameter)findChildByRoleAsPsiElement(ChildRole.PARAMETER);
  }

  public PsiCodeBlock getCatchBlock() {
    return (PsiCodeBlock)findChildByRoleAsPsiElement(ChildRole.CATCH_BLOCK);
  }

  public PsiType getCatchType() {
    PsiParameter parameter = getParameter();
    if (parameter == null) return null;
    return parameter.getType();
  }

  @NotNull
  public PsiTryStatement getTryStatement() {
    return (PsiTryStatement)getParent();
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitCatchSection(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiCatchSection";
  }

  public ASTNode findChildByRole(int role) {
    switch(role) {
      default:
        return null;

      case ChildRole.PARAMETER:
        return TreeUtil.findChild(this, PARAMETER);

      case ChildRole.CATCH_KEYWORD:
        return TreeUtil.findChild(this, CATCH_KEYWORD);

      case ChildRole.CATCH_BLOCK_PARAMETER_LPARENTH:
        return TreeUtil.findChild(this, LPARENTH);

      case ChildRole.CATCH_BLOCK_PARAMETER_RPARENTH:
        return TreeUtil.findChild(this, RPARENTH);

      case ChildRole.CATCH_BLOCK:
        return TreeUtil.findChild(this, CODE_BLOCK);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == PARAMETER) {
      return ChildRole.PARAMETER;
    } else if (i == CODE_BLOCK) {
      return ChildRole.CATCH_BLOCK;
    } else if (i == CATCH_KEYWORD) {
      return ChildRole.CATCH_KEYWORD;
    } else if (i == LPARENTH) {
      return ChildRole.CATCH_BLOCK_PARAMETER_LPARENTH;
    } else if (i == RPARENTH) {
      return ChildRole.CATCH_BLOCK_PARAMETER_RPARENTH;
    }

    return ChildRoleBase.NONE;
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if (lastParent == null || lastParent.getParent() != this)
      // Parent element should not see our vars
      return true;

    final PsiParameter catchParameter = getParameter();
    if (catchParameter != null) {
      return processor.execute(catchParameter, state);
    }

    return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
  }
}
