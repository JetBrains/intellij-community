package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.PomField;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.cache.FieldView;
import com.intellij.psi.impl.cache.InitializerTooLongException;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.parsing.ExpressionParsing;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatchedSoftReference;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class PsiFieldImpl extends NonSlaveRepositoryPsiElement implements PsiField, PsiVariableEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiFieldImpl");

  private PsiModifierListImpl myRepositoryModifierList = null;

  private String myCachedName = null;
  private PatchedSoftReference<PsiType> myCachedType = null;
  private long myCachedFirstFieldInDeclId = -1;
  private Boolean myCachedIsDeprecated = null;
  private String myCachedInitializerText = null;
  private Object myCachedInitializerValue = null; // PsiExpression on constant value for literal

  public PsiFieldImpl(PsiManagerImpl manager, long repositoryId) {
    super(manager, repositoryId);
  }

  public PsiFieldImpl(PsiManagerImpl manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    dropCached();
  }

  private void dropCached() {
    myCachedName = null;
    myCachedInitializerText = null;
    myCachedIsDeprecated = null;
    myCachedInitializerValue = null;
    myCachedType = null;
    myCachedFirstFieldInDeclId = -1;
  }

  protected Object clone() {
    PsiFieldImpl clone = (PsiFieldImpl)super.clone();
    clone.myRepositoryModifierList = null;
    clone.dropCached();
    return clone;
  }

  public void setRepositoryId(long repositoryId) {
    super.setRepositoryId(repositoryId);

    if (repositoryId < 0){
      if (myRepositoryModifierList != null){
        myRepositoryModifierList.setOwner(this);
        myRepositoryModifierList = null;
      }
    }
    else{
      myRepositoryModifierList = (PsiModifierListImpl)bindSlave(ChildRole.MODIFIER_LIST);
    }

    dropCached();
  }

  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : PsiTreeUtil.getParentOfType(this, JspClass.class);
  }

  public final PsiIdentifier getNameIdentifier(){
    return (PsiIdentifier)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  public String getName() {
    if (myCachedName == null){
      if (getTreeElement() != null){
        myCachedName = getNameIdentifier().getText();
      }
      else{
          myCachedName = getRepositoryManager().getFieldView().getName(getRepositoryId());
        }
      }
    return myCachedName;
  }

  public PsiElement setName(String name) throws IncorrectOperationException{
    SharedPsiElementImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @NotNull
  public PsiType getType(){
    if (getTreeElement() != null) {
      myCachedType = null;
      return SharedImplUtil.getType(this);
    }
    else {
      if (myCachedType != null) {
        PsiType type = myCachedType.get();
        if (type != null) return type;
      }

      String typeText = getRepositoryManager().getFieldView().getTypeText(getRepositoryId());
      try {
        final PsiType type = myManager.getParserFacade().createTypeFromText(typeText, this);
        myCachedType = new PatchedSoftReference<PsiType>(type);
        return type;
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }
  }

  public PsiTypeElement getTypeElement(){
    PsiField firstField = findFirstFieldInDeclaration();
    if (firstField != this){
      return firstField.getTypeElement();
    }

    return (PsiTypeElement)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.TYPE);
  }

  public PsiModifierList getModifierList(){
    PsiField firstField = findFirstFieldInDeclaration();
    if (firstField != this){
      return firstField.getModifierList();
    }

    if (getRepositoryId() >= 0){
      if (myRepositoryModifierList == null){
        myRepositoryModifierList = new PsiModifierListImpl(myManager, this);
      }
      return myRepositoryModifierList;
    }
    else{
      return (PsiModifierList)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.MODIFIER_LIST);
    }
  }

  public boolean hasModifierProperty(String name) {
    return getModifierList().hasModifierProperty(name);
  }

  private PsiField findFirstFieldInDeclaration(){
    if (myRepositoryModifierList != null) return this; // optmization

      CompositeElement treeElement = getTreeElement();
      if (treeElement != null){
        ASTNode modifierList = treeElement.findChildByRole(ChildRole.MODIFIER_LIST);
        if (modifierList != null) {
          return this;
        }
        else{
          ASTNode prevField = treeElement.getTreePrev();
          while(prevField.getElementType() != JavaElementType.FIELD){
            prevField = prevField.getTreePrev();
          }
          return ((PsiFieldImpl)SourceTreeToPsiMap.treeElementToPsi(prevField)).findFirstFieldInDeclaration();
        }
      }
      else{
        long repositoryId = getRepositoryId();
        if (myCachedFirstFieldInDeclId < 0){
          myCachedFirstFieldInDeclId = getRepositoryManager().getFieldView().getFirstFieldInDeclaration(repositoryId);
        }
        long repositoryId1 = myCachedFirstFieldInDeclId;
        if (repositoryId1 == repositoryId){
          return this;
        }
        else{
          return (PsiField)getRepositoryElementsManager().findOrCreatePsiElementById(repositoryId1);
        }
      }
    }

  public PsiExpression getInitializer() {
    return (PsiExpression)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.INITIALIZER);
  }

  public boolean hasInitializer() {
    if (getTreeElement() != null){
      return getInitializer() != null;
    }
    else{
      try{
        return getInitializerText() != null;
      }
      catch(InitializerTooLongException e){
        calcTreeElement();
        return hasInitializer();
      }
    }
  }

  public PomField getPom() {
    //TODO:
    return null;
  }

  private static class OurConstValueComputer implements ResolveCache.ConstValueComputer{
    private static final OurConstValueComputer INSTANCE = new OurConstValueComputer();

    public Object execute(PsiVariable variable, Set<PsiVariable> visitedVars) {
      return ((PsiFieldImpl)variable)._computeConstantValue(visitedVars);
    }
  }

  private Object _computeConstantValue(Set<PsiVariable> visitedVars) {
    if (myCachedInitializerValue != null && !(myCachedInitializerValue instanceof PsiExpression)){
      return myCachedInitializerValue;
    }

    PsiType type = getType();
    if (type == null) return null;
    // javac rejects all non primitive and non String constants, although JLS states constants "variables whose initializers are constant expressions"
    if (!(type instanceof PsiPrimitiveType) && !type.equalsToText("java.lang.String")) return null;

    PsiExpression initializer;
    if (myCachedInitializerValue instanceof PsiExpression){
      initializer = (PsiExpression)myCachedInitializerValue;
    }
    else{
      if (getTreeElement() != null){
        initializer = getInitializer();
        if (initializer == null) return null;
      }
      else{
        try{
          String initializerText = getInitializerText();
          if (initializerText == null) return null;
          final FileElement holderElement = new DummyHolder(myManager, this).getTreeElement();
          CompositeElement exprElement = ExpressionParsing.parseExpressionText(myManager, initializerText, 0, initializerText.length(), holderElement.getCharTable());
          TreeUtil.addChildren(holderElement, exprElement);
          initializer = (PsiExpression)SourceTreeToPsiMap.treeElementToPsi(exprElement);
        }
        catch(InitializerTooLongException e){
          calcTreeElement();
          return computeConstantValue(visitedVars);
        }
      }
    }

    Object result = PsiConstantEvaluationHelperImpl.computeCastTo(initializer, type, visitedVars);

    if (initializer instanceof PsiLiteralExpression){
      myCachedInitializerValue = result;
    }
    else{
      myCachedInitializerValue = initializer;
    }


    return result;
  }

  public Object computeConstantValue() {
    if (myCachedInitializerValue != null && !(myCachedInitializerValue instanceof PsiExpression)){
      return myCachedInitializerValue;
    }

    return computeConstantValue(new HashSet<PsiVariable>(2));
  }

  public Object computeConstantValue(Set<PsiVariable> visitedVars) {
    if (!hasModifierProperty(PsiModifier.FINAL)) return null;

    return myManager.getResolveCache().computeConstantValueWithCaching(this, OurConstValueComputer.INSTANCE, visitedVars);
  }

  private String getInitializerText() throws InitializerTooLongException {
    if (myCachedInitializerText == null){
        long repositoryId = getRepositoryId();
        myCachedInitializerText = getRepositoryManager().getFieldView().getInitializerText(repositoryId);
        if (myCachedInitializerText == null) myCachedInitializerText = "";
      }
    return myCachedInitializerText.length() > 0 ? myCachedInitializerText : null;
  }

  public boolean isDeprecated() {
    if (myCachedIsDeprecated == null){
      boolean deprecated;
      if (getTreeElement() != null){
        PsiDocComment docComment = getDocComment();
        deprecated = docComment != null && getDocComment().findTagByName("deprecated") != null;
        if (!deprecated) {
          deprecated = getModifierList().findAnnotation("java.lang.Deprecated") != null;
        }
      }
      else{
          FieldView fieldView = getRepositoryManager().getFieldView();
          deprecated = fieldView.isDeprecated(getRepositoryId());
          if (!deprecated && fieldView.mayBeDeprecatedByAnnotation(getRepositoryId())) {
            deprecated = getModifierList().findAnnotation("java.lang.Deprecated") != null;
          }
        }
      myCachedIsDeprecated = deprecated ? Boolean.TRUE : Boolean.FALSE;
    }
    return myCachedIsDeprecated.booleanValue();
  }

  public PsiDocComment getDocComment(){
    CompositeElement treeElement = calcTreeElement();
    if (getTypeElement() != null) {
      return (PsiDocComment)treeElement.findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
    }
    else{
      ASTNode prevField = treeElement.getTreePrev();
      while(prevField.getElementType() != JavaElementType.FIELD){
        prevField = prevField.getTreePrev();
      }
      return ((PsiField)SourceTreeToPsiMap.treeElementToPsi(prevField)).getDocComment();
    }
  }

  public void normalizeDeclaration() throws IncorrectOperationException{
    CheckUtil.checkWritable(this);

    final PsiTypeElement type = getTypeElement();
    PsiElement modifierList = getModifierList();
    ASTNode field = SourceTreeToPsiMap.psiElementToTree(type.getParent());
    while(true){
      ASTNode comma = TreeUtil.skipElements(field.getTreeNext(), ElementType.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (comma == null || comma.getElementType() != JavaTokenType.COMMA) break;
      ASTNode nextField = TreeUtil.skipElements(comma.getTreeNext(), ElementType.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (nextField == null || nextField.getElementType() != JavaElementType.FIELD) break;

      TreeElement semicolon = Factory.createSingleLeafElement(JavaTokenType.SEMICOLON, ";", 0, 1, null, getManager());
      CodeEditUtil.addChild((CompositeElement)field, semicolon, null);

      CodeEditUtil.removeChild((CompositeElement)comma.getTreeParent(), comma);

      PsiElement typeClone = type.copy();
      CodeEditUtil.addChild((CompositeElement)nextField, (TreeElement)SourceTreeToPsiMap.psiElementToTree(typeClone),
                            nextField.getFirstChildNode());

      PsiElement modifierListClone = modifierList.copy();
      CodeEditUtil.addChild((CompositeElement)nextField, SourceTreeToPsiMap.psiElementToTree(modifierListClone), nextField.getFirstChildNode());

      field = nextField;
    }

    SharedImplUtil.normalizeBrackets(this);
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitField(this);
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place){
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    return true;
  }

  public String toString(){
    return "PsiField:" + getName();
  }

  public PsiElement getOriginalElement() {
    PsiClass originalClass = (PsiClass)getContainingClass().getOriginalElement();
    PsiField originalField = originalClass.findFieldByName(getName(), false);
    return originalField != null ? originalField : this;
  }

  public ItemPresentation getPresentation() {
    return SymbolPresentationUtil.getFieldPresentation(this);
  }

  public void setInitializer(PsiExpression initializer) throws IncorrectOperationException {
    SharedImplUtil.setInitializer(this, initializer);
  }

}

