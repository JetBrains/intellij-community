package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspExpressionStatement;
import com.intellij.psi.impl.source.jsp.jspJava.JspTemplateStatement;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class PsiCodeBlockImpl extends CompositePsiElement implements PsiCodeBlock{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiCodeBlockImpl");

  public PsiCodeBlockImpl() {
    super(CODE_BLOCK);
  }

  public void clearCaches() {
    super.clearCaches();
    myVariablesSet = null;
    myClassesSet = null;
    myConflict = false;
  }

  @NotNull
  public PsiStatement[] getStatements() {
    return getChildrenAsPsiElements(STATEMENT_BIT_SET, PSI_STATEMENT_ARRAY_CONSTRUCTOR);
  }

  public PsiElement getFirstBodyElement() {
    final PsiElement nextSibling = getLBrace().getNextSibling();
    return nextSibling == getRBrace() ? null : nextSibling;
  }

  public PsiElement getLastBodyElement() {
    final PsiJavaToken rBrace = getRBrace();
    if (rBrace != null) {
      final PsiElement prevSibling = rBrace.getPrevSibling();
      return prevSibling == getLBrace() ? null : prevSibling;
    }
    return getLastChild();
  }

  public PsiJavaToken getLBrace() {
    return (PsiJavaToken)findChildByRoleAsPsiElement(ChildRole.LBRACE);
  }

  public PsiJavaToken getRBrace() {
    return (PsiJavaToken)findChildByRoleAsPsiElement(ChildRole.RBRACE);
  }

  private volatile Set<String> myVariablesSet = null;
  private volatile Set<String> myClassesSet = null;
  private volatile boolean myConflict = false;

  // return Pair(classesset, localsSet) or null if there was conflict
  private Pair<Set<String>, Set<String>> buildMaps() {
    Set<String> set1 = myClassesSet;
    Set<String> set2 = myVariablesSet;
    boolean wasConflict = myConflict;
    if (set1 == null || set2 == null) {
      final Set<String> localsSet = new THashSet<String>();
      final Set<String> classesSet = new THashSet<String>();
      final Ref<Boolean> conflict = new Ref<Boolean>(Boolean.FALSE);
      PsiScopesUtil.walkChildrenScopes(this, new BaseScopeProcessor() {
        public boolean execute(PsiElement element, ResolveState state) {
          if (element instanceof PsiLocalVariable) {
            final PsiLocalVariable variable = (PsiLocalVariable)element;
            final String name = variable.getName();
            if (!localsSet.add(name)) {
              conflict.set(Boolean.TRUE);
              localsSet.clear();
              classesSet.clear();
            }
          }
          else if (element instanceof PsiClass) {
            final PsiClass psiClass = (PsiClass)element;
            final String name = psiClass.getName();
            if (!classesSet.add(name)) {
              conflict.set(Boolean.TRUE);
              localsSet.clear();
              classesSet.clear();
            }
          }
          return !conflict.get();
        }
      }, ResolveState.initial(), this, this);

      myClassesSet = set1 = classesSet;
      myVariablesSet = set2 = localsSet;
      myConflict = wasConflict = conflict.get();
    }
    return wasConflict ? null : Pair.create(set1, set2);
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    ChameleonTransforming.transformChildren(this);
    if (anchor == null){
      if (before == null || before.booleanValue()){
        anchor = findChildByRole(ChildRole.RBRACE);
        before = Boolean.TRUE;
      }
      else{
        anchor = findChildByRole(ChildRole.LBRACE);
        before = Boolean.FALSE;
      }
    }

    if (before == Boolean.TRUE) {
      while (anchor instanceof JspExpressionStatement || anchor instanceof JspTemplateStatement) {
        anchor = anchor.getTreePrev();
        before = Boolean.FALSE;
      }
    }
    else if (before == Boolean.FALSE) {
      while (anchor instanceof JspExpressionStatement || anchor instanceof JspTemplateStatement) {
        anchor = anchor.getTreeNext();
        before = Boolean.TRUE;
      }
    }

    return super.addInternal(first, last, anchor, before);
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    ChameleonTransforming.transformChildren(this);
    switch(role){
      default:
        return null;

      case ChildRole.LBRACE:
        return TreeUtil.findChild(this, LBRACE);

      case ChildRole.RBRACE:
        return TreeUtil.findChildBackward(this, RBRACE);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == LBRACE) {
      return getChildRole(child, ChildRole.LBRACE);
    }
    else if (i == RBRACE) {
      return getChildRole(child, ChildRole.RBRACE);
    }
    else {
      if (ElementType.STATEMENT_BIT_SET.contains(child.getElementType())) {
        return ChildRole.STATEMENT_IN_BLOCK;
      }
      return ChildRole.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitCodeBlock(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiCodeBlock";
  }


  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if (lastParent == null) {
      // Parent element should not see our vars
      return true;
    }
    Pair<Set<String>, Set<String>> pair = buildMaps();
    boolean conflict = pair == null;
    final Set<String> classesSet = conflict ? null : pair.getFirst();
    final Set<String> variablesSet = conflict ? null : pair.getSecond();
    final NameHint hint = processor.getHint(NameHint.class);
    if (hint != null && !conflict) {
      final ElementClassHint elementClassHint = processor.getHint(ElementClassHint.class);
      final String name = hint.getName();
      if ((elementClassHint == null || elementClassHint.shouldProcess(PsiClass.class)) && classesSet.contains(name)) {
        return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
      }
      if ((elementClassHint == null || elementClassHint.shouldProcess(PsiVariable.class)) && variablesSet.contains(name)) {
        return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
      }
    }
    else {
      return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
    }
    return true;
  }
}
