package com.intellij.psi.impl.source.tree.java;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiConstantEvaluationHelperImpl;
import com.intellij.psi.impl.PsiVariableEx;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;

import java.util.HashSet;
import java.util.Set;

public class PsiLocalVariableImpl extends CompositePsiElement implements PsiLocalVariable, PsiVariableEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiLocalVariableImpl");

  private String myCachedName = null;

  public PsiLocalVariableImpl() {
    super(LOCAL_VARIABLE);
  }

  public void clearCaches() {
    super.clearCaches();
    myCachedName = null;
  }

  public final PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  public final String getName() {
    if (myCachedName == null){
      myCachedName = getNameIdentifier().getText();
    }
    return myCachedName;
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    SharedPsiElementImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  public final PsiType getType() {
    return SharedImplUtil.getType(this);
  }

  public PsiTypeElement getTypeElement() {
    CompositeElement first = (CompositeElement)TreeUtil.findChild(getTreeParent(), LOCAL_VARIABLE);
    return (PsiTypeElement)SourceTreeToPsiMap.treeElementToPsi(TreeUtil.findChild(first, TYPE));
  }

  public PsiModifierList getModifierList() {
    CompositeElement first = (CompositeElement)TreeUtil.findChild(getTreeParent(), LOCAL_VARIABLE);
    return (PsiModifierList)first.findChildByRoleAsPsiElement(ChildRole.MODIFIER_LIST);
  }

  public boolean hasModifierProperty(String name) {
    return getModifierList().hasModifierProperty(name);
  }

  public PsiExpression getInitializer() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.INITIALIZER);
  }

  public boolean hasInitializer() {
    return getInitializer() != null;
  }

  public Object computeConstantValue() {
    return computeConstantValue(new HashSet());
  }

  public Object computeConstantValue(Set visitedVars) {
    if (!hasModifierProperty(PsiModifier.FINAL)) return null;

    PsiType type = getType();
    if (type == null) return null;
    // javac rejects all non primitive and non String constants, although JLS states constants "variables whose initializers are constant expressions"
    if (!(type instanceof PsiPrimitiveType) && !type.equalsToText("java.lang.String")) return null;

    PsiExpression initializer = getInitializer();
    if (initializer == null) return null;
    return PsiConstantEvaluationHelperImpl.computeCastTo(initializer, getType(), visitedVars);
  }

  public int getTextOffset() {
    return getNameIdentifier().getTextOffset();
  }

  public void normalizeDeclaration() throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);

    CompositeElement statement = getTreeParent();
    PsiElement[] variables = ((PsiDeclarationStatement)SourceTreeToPsiMap.treeElementToPsi(statement)).getDeclaredElements();
    if (variables.length > 1){
      //CodeStyleManagerImpl codeStyleManager = (CodeStyleManagerImpl)getManager().getCodeStyleManager();
      TreeElement type = SourceTreeToPsiMap.psiElementToTree(getTypeElement());
      TreeElement modifierList = SourceTreeToPsiMap.psiElementToTree(getModifierList());
      TreeElement last = statement;
      for(int i = 1; i < variables.length; i++){
        CompositeElement variable = (CompositeElement)SourceTreeToPsiMap.psiElementToTree(variables[i]);

        TreeElement comma = TreeUtil.skipElementsBack(variable.getTreePrev(), ElementType.WHITE_SPACE_OR_COMMENT_BIT_SET);
        if (comma != null && comma.getElementType() == JavaTokenType.COMMA){
          CodeEditUtil.removeChildren(statement, comma, variable.getTreePrev());
        }

        CodeEditUtil.removeChild(statement, variable);
        final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(statement);
        variable.putUserData(CharTable.CHAR_TABLE_KEY, charTableByTree);
        CompositeElement statement1 = Factory.createCompositeElement(DECLARATION_STATEMENT);
        statement1.putUserData(CharTable.CHAR_TABLE_KEY, charTableByTree);
        ChangeUtil.addChild(statement1, variable, null);

        TreeElement space = Factory.createSingleLeafElement(JavaTokenType.WHITE_SPACE, new char[]{' '}, 0, 1, treeCharTab, getManager());
        ChangeUtil.addChild(variable, space, variable.firstChild);

        TreeElement typeClone = (TreeElement)type.clone();
        typeClone.putUserData(CharTable.CHAR_TABLE_KEY, treeCharTab);
        ChangeUtil.addChild(variable, typeClone, variable.firstChild);

        if (modifierList.getTextLength() > 0){
          space = Factory.createSingleLeafElement(JavaTokenType.WHITE_SPACE, new char[]{' '}, 0, 1, treeCharTab, getManager());
          ChangeUtil.addChild(variable, space, variable.firstChild);
        }

        TreeElement modifierListClone = (TreeElement)modifierList.clone();
        modifierListClone.putUserData(CharTable.CHAR_TABLE_KEY, treeCharTab);
        ChangeUtil.addChild(variable, modifierListClone, variable.firstChild);

        TreeElement semicolon = Factory.createSingleLeafElement(JavaTokenType.SEMICOLON, new char[]{';'}, 0, 1, treeCharTab, getManager());
        ChangeUtil.addChild((CompositeElement)SourceTreeToPsiMap.psiElementToTree(variables[i - 1]), semicolon, null);

        CodeEditUtil.addChild(statement.getTreeParent(), statement1, last.getTreeNext());

        //?
        //codeStyleManager.adjustInsertedCode(statement1);
        last = statement1;
      }
    }

    SharedImplUtil.normalizeBrackets(this);
  }

  public void deleteChildInternal(TreeElement child) {
    if (getChildRole(child) == ChildRole.INITIALIZER){
      TreeElement eq = findChildByRole(ChildRole.INITIALIZER_EQ);
      if (eq != null){
        deleteChildInternal(eq);
      }
    }
    super.deleteChildInternal(child);
  }

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.MODIFIER_LIST:
        return TreeUtil.findChild(this, MODIFIER_LIST);

      case ChildRole.TYPE:
        return TreeUtil.findChild(this, TYPE);

      case ChildRole.NAME:
        return TreeUtil.findChild(this, JavaTokenType.IDENTIFIER);

      case ChildRole.INITIALIZER_EQ:
        return TreeUtil.findChild(this, JavaTokenType.EQ);

      case ChildRole.INITIALIZER:
        return TreeUtil.findChild(this, ElementType.EXPRESSION_BIT_SET);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, JavaTokenType.SEMICOLON);
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else if (i == TYPE) {
      return getChildRole(child, ChildRole.TYPE);
    }
    else if (i == JavaTokenType.IDENTIFIER) {
      return getChildRole(child, ChildRole.NAME);
    }
    else if (i == JavaTokenType.EQ) {
      return getChildRole(child, ChildRole.INITIALIZER_EQ);
    }
    else if (i == JavaTokenType.SEMICOLON) {
      return getChildRole(child, ChildRole.CLOSING_SEMICOLON);
    }
    else {
      if (ElementType.EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.INITIALIZER;
      }
      return ChildRole.NONE;
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitLocalVariable(this);
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    if (lastParent == null) return true;
    if (lastParent.getParent() != this) return true;
    final TreeElement lastParentTree = SourceTreeToPsiMap.psiElementToTree(lastParent);

    if (getChildRole(lastParentTree) == ChildRole.INITIALIZER)
      return processor.execute(this, substitutor);
    return true;
  }

  public ItemPresentation getPresentation() {
    return SymbolPresentationUtil.getVariablePresentation(this);
  }

  public String toString() {
    return "PsiLocalVariable:" + getName();
  }
}
