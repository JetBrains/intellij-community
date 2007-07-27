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

  private volatile String myCachedName = null;
  private volatile PatchedSoftReference<PsiType> myCachedType = null;
  private volatile long myCachedFirstFieldInDeclId = -1;
  private volatile Boolean myCachedIsDeprecated = null;
  private volatile String myCachedInitializerText = null;
  private volatile Object myCachedInitializerValue = null; // PsiExpression on constant value for literal

  public PsiFieldImpl(PsiManagerEx manager, long repositoryId) {
    super(manager, repositoryId);
  }

  public PsiFieldImpl(PsiManagerEx manager, RepositoryTreeElement treeElement) {
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

  @NotNull
  public final PsiIdentifier getNameIdentifier(){
    return (PsiIdentifier)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  public String getName() {
    String cachedName = myCachedName;
    if (cachedName == null) {
      if (getTreeElement() != null) {
        cachedName = myCachedName = getNameIdentifier().getText();
      }
      else {
        cachedName = myCachedName = getRepositoryManager().getFieldView().getName(getRepositoryId());
      }
    }
    return cachedName;
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException{
    SharedPsiElementImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @NotNull
  public PsiType getType(){
    if (getTreeElement() == null) {
      PatchedSoftReference<PsiType> cachedType = myCachedType;
      if (cachedType != null) {
        PsiType type = cachedType.get();
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
    else {
      myCachedType = null;
      return SharedImplUtil.getType(this);
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
      synchronized (PsiLock.LOCK) {
        if (myRepositoryModifierList == null){
          myRepositoryModifierList = new PsiModifierListImpl(myManager, this);
        }
        return myRepositoryModifierList;
      }
    }
    else{
      return (PsiModifierList)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.MODIFIER_LIST);
    }
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  private PsiField findFirstFieldInDeclaration() {
    if (myRepositoryModifierList != null) return this; // optmization

    CompositeElement treeElement = getTreeElement();
    if (treeElement == null) {
      long repositoryId = getRepositoryId();
      long cachedFirstFieldInDeclId = myCachedFirstFieldInDeclId;
      if (cachedFirstFieldInDeclId < 0) {
        cachedFirstFieldInDeclId = myCachedFirstFieldInDeclId = getRepositoryManager().getFieldView().getFirstFieldInDeclaration(repositoryId);
      }
      long repositoryId1 = cachedFirstFieldInDeclId;
      if (repositoryId1 == repositoryId) {
        return this;
      }
      else {
        return (PsiField)getRepositoryElementsManager().findOrCreatePsiElementById(repositoryId1);
      }
    }
    else {
      ASTNode modifierList = treeElement.findChildByRole(ChildRole.MODIFIER_LIST);
      if (modifierList == null) {
        ASTNode prevField = treeElement.getTreePrev();
        while (prevField != null && prevField.getElementType() != JavaElementType.FIELD) {
          prevField = prevField.getTreePrev();
        }
        if (prevField == null) return this;
        return ((PsiFieldImpl)SourceTreeToPsiMap.treeElementToPsi(prevField)).findFirstFieldInDeclaration();
      }
      else {
        return this;
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
    Object cachedInitializerValue = myCachedInitializerValue;
    if (cachedInitializerValue != null && !(cachedInitializerValue instanceof PsiExpression)){
      return cachedInitializerValue;
    }

    PsiType type = getType();
    // javac rejects all non primitive and non String constants, although JLS states constants "variables whose initializers are constant expressions"
    if (!(type instanceof PsiPrimitiveType) && !type.equalsToText("java.lang.String")) return null;

    PsiExpression initializer;
    if (cachedInitializerValue != null) {
      initializer = (PsiExpression)cachedInitializerValue;
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
    Object cachedInitializerValue = myCachedInitializerValue;
    if (cachedInitializerValue != null && !(cachedInitializerValue instanceof PsiExpression)){
      return cachedInitializerValue;
    }

    return computeConstantValue(new HashSet<PsiVariable>(2));
  }

  public Object computeConstantValue(Set<PsiVariable> visitedVars) {
    if (!hasModifierProperty(PsiModifier.FINAL)) return null;

    return myManager.getResolveCache().computeConstantValueWithCaching(this, OurConstValueComputer.INSTANCE, visitedVars);
  }

  private String getInitializerText() throws InitializerTooLongException {
    String initializerText = myCachedInitializerText;
    if (initializerText == null) {
      long repositoryId = getRepositoryId();
      initializerText = getRepositoryManager().getFieldView().getInitializerText(repositoryId);
      if (initializerText == null) initializerText = "";
      myCachedInitializerText = initializerText;
    }
    return initializerText.length() > 0 ? initializerText : null;
  }

  public boolean isDeprecated() {
    Boolean isDeprecated = myCachedIsDeprecated;
    if (isDeprecated == null) {
      boolean deprecated;
      if (getTreeElement() != null) {
        PsiDocComment docComment = getDocComment();
        deprecated = docComment != null && getDocComment().findTagByName("deprecated") != null;
        if (!deprecated) {
          deprecated = getModifierList().findAnnotation("java.lang.Deprecated") != null;
        }
      }
      else {
        FieldView fieldView = getRepositoryManager().getFieldView();
        deprecated = fieldView.isDeprecated(getRepositoryId());
        if (!deprecated && fieldView.mayBeDeprecatedByAnnotation(getRepositoryId())) {
          deprecated = getModifierList().findAnnotation("java.lang.Deprecated") != null;
        }
      }
      isDeprecated = myCachedIsDeprecated = deprecated ? Boolean.TRUE : Boolean.FALSE;
    }
    return isDeprecated.booleanValue();
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
      ASTNode comma = TreeUtil.skipElements(field.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (comma == null || comma.getElementType() != JavaTokenType.COMMA) break;
      ASTNode nextField = TreeUtil.skipElements(comma.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (nextField == null || nextField.getElementType() != JavaElementType.FIELD) break;

      TreeElement semicolon = Factory.createSingleLeafElement(JavaTokenType.SEMICOLON, ";", 0, 1, null, getManager());
      CodeEditUtil.addChild((CompositeElement)field, semicolon, null);

      CodeEditUtil.removeChild((CompositeElement)comma.getTreeParent(), comma);

      PsiElement typeClone = type.copy();
      CodeEditUtil.addChild((CompositeElement)nextField, SourceTreeToPsiMap.psiElementToTree(typeClone), nextField.getFirstChildNode());

      PsiElement modifierListClone = modifierList.copy();
      CodeEditUtil.addChild((CompositeElement)nextField, SourceTreeToPsiMap.psiElementToTree(modifierListClone), nextField.getFirstChildNode());

      field = nextField;
    }

    SharedImplUtil.normalizeBrackets(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitField(this);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place){
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

