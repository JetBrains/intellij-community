package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class PsiCodeBlockImpl extends CompositePsiElement implements PsiCodeBlock{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiCodeBlockImpl");

  public PsiCodeBlockImpl() {
    super(CODE_BLOCK);
  }

  public synchronized void clearCaches() {
    super.clearCaches();
    myProcessed = false;
    myConflict = false;
    myVariablesSet = null;
    myClassesSet = null;
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

  private Set<String> myVariablesSet = null;
  private Set<String> myClassesSet = null;
  private boolean myConflict = false;
  private boolean myProcessed = false;

  private synchronized void buildMaps(){
    if(myProcessed) return;
    myProcessed = true;
    PsiScopesUtil.walkChildrenScopes(this, new BaseScopeProcessor() {
      public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
        if(element instanceof PsiLocalVariable){
          final PsiLocalVariable variable = (PsiLocalVariable)element;
          final String name = variable.getName();
          Set<String> localsSet = myVariablesSet;
          if(localsSet != null && localsSet.contains(name)){
            myConflict = true;
            myVariablesSet = null;
            myClassesSet = null;
          }
          else {
            if (localsSet == null) localsSet = new HashSet<String>();
            localsSet.add(name);
            myVariablesSet = localsSet;
          }
        }
        else if(element instanceof PsiClass){
          final PsiClass psiClass = (PsiClass)element;
          final String name = psiClass.getName();
          Set<String> classesSet = myClassesSet;
          if(classesSet != null && classesSet.contains(name)){
            myConflict = true;
            myVariablesSet = null;
            myClassesSet = null;
          }
          else {
            if (classesSet == null) classesSet = new HashSet<String>();
            classesSet.add(name);
            myClassesSet = classesSet;
          }
        }
        return !myConflict;
      }
    }, PsiSubstitutor.EMPTY, this, this);
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

  public void accept(PsiElementVisitor visitor) {
    visitor.visitCodeBlock(this);
  }

  public String toString() {
    return "PsiCodeBlock";
  }


  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if (lastParent == null)
    // Parent element should not see our vars
      return true;
    buildMaps();
    final NameHint hint = processor.getHint(NameHint.class);
    if (hint != null && !myConflict) {
      final ElementClassHint elementClassHint = processor.getHint(ElementClassHint.class);
      final String name = hint.getName();
      if (myClassesSet != null && (elementClassHint == null || elementClassHint.shouldProcess(PsiClass.class))) {
        if (myClassesSet.contains(name)) {
          return PsiScopesUtil.walkChildrenScopes(this, processor, substitutor, lastParent, place);
        }
      }
      if (myVariablesSet != null && (elementClassHint == null || elementClassHint.shouldProcess(PsiVariable.class))) {
        if (myVariablesSet.contains(name)) {
          return PsiScopesUtil.walkChildrenScopes(this, processor, substitutor, lastParent, place);
        }
      }
    }
    else {
      if (myConflict || (myVariablesSet != null || myClassesSet != null)) {
        return PsiScopesUtil.walkChildrenScopes(this, processor, substitutor, lastParent, place);
      }
    }
    return true;
  }
}
