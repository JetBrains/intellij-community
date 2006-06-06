package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ConstructorFilter;
import com.intellij.psi.filters.NotFilter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiSubstitutorEx;
import com.intellij.psi.impl.source.SourceJavaCodeReference;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerEx;
import com.intellij.psi.impl.source.parsing.ExpressionParsing;
import com.intellij.psi.impl.source.resolve.ClassResolverProcessor;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.VariableResolverProcessor;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiReferenceExpressionImpl extends CompositePsiElement implements PsiReferenceExpression, SourceJavaCodeReference {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl");

  private String myCachedQName = null;
  private String myCachedTextSkipWhiteSpaceAndComments = null;

  public PsiReferenceExpressionImpl() {
    super(REFERENCE_EXPRESSION);
  }

  public PsiExpression getQualifierExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.QUALIFIER);
  }

  public PsiElement bindToElementViaStaticImport(PsiClass qualifierClass) throws IncorrectOperationException {
    if (qualifierClass == null || qualifierClass.getQualifiedName() == null) throw new IncorrectOperationException();

    String staticName = getReferenceName();
    if (getQualifierExpression() == null) {
      PsiImportList importList = ((PsiJavaFile)getContainingFile()).getImportList();
      PsiImportStatementBase singleImportStatement = importList.findSingleImportStatement(staticName);
      if (singleImportStatement != null) {
        if (singleImportStatement instanceof PsiImportStaticStatement) {
          String qName = qualifierClass.getQualifiedName() + "." + staticName;
          if (singleImportStatement.getImportReference().getQualifiedName().equals(qName)) return this;
        }
        String qualifiedName = qualifierClass.getQualifiedName();
        if (qualifiedName != null) {
          PsiReferenceExpression classRef = getManager().getElementFactory().createReferenceExpression(qualifierClass);
          final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
          LeafElement dot = Factory.createSingleLeafElement(ElementType.DOT, new char[]{'.'}, 0, 1, treeCharTab, getManager());
          addInternal(dot, dot, SourceTreeToPsiMap.psiElementToTree(getParameterList()), Boolean.TRUE);
          addBefore(classRef, SourceTreeToPsiMap.treeElementToPsi(dot));
          return this;
        }
      }
      else {
        importList.add(getManager().getElementFactory().createImportStaticStatement(qualifierClass, staticName));
        return this;
      }
    }

    throw new IncorrectOperationException();
  }

  public void setQualifierExpression(@Nullable PsiExpression newQualifier) throws IncorrectOperationException {
    final PsiExpression oldQualifier = getQualifierExpression();
    if (newQualifier == null) {
      if (oldQualifier != null) {
        deleteChildInternal(oldQualifier.getNode());
      }
    } else {
      if (oldQualifier != null) {
        oldQualifier.replace(newQualifier);
      } else {
        final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
        TreeElement dot = (TreeElement)findChildByRole(ChildRole.DOT);
        if (dot == null) {
          dot = Factory.createSingleLeafElement(DOT, new char[]{'.'}, 0, 1, treeCharTab, getManager());
          dot = addInternal(dot, dot, getFirstChildNode(), Boolean.TRUE);
        }
        addBefore(newQualifier, dot.getPsi());
      }
    }
  }

  public PsiElement getQualifier() {
    return getQualifierExpression();
  }

  public PsiReference getReference() {
    return this;
  }

  public PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  public void clearCaches() {
    myCachedQName = null;
    myCachedTextSkipWhiteSpaceAndComments = null;
    super.clearCaches();
  }

  private static final class OurGenericsResolver implements ResolveCache.PolyVariantResolver {
    public static final OurGenericsResolver INSTANCE = new OurGenericsResolver();

    public JavaResolveResult[] _resolve(PsiJavaReference ref, boolean incompleteCode) {
      final PsiReferenceExpressionImpl _ref = (PsiReferenceExpressionImpl)ref;
      IElementType parentType = _ref.getTreeParent() != null ? _ref.getTreeParent().getElementType() : null;
      final JavaResolveResult[] result = _ref._resolve(parentType);

      if (incompleteCode && parentType != REFERENCE_EXPRESSION && result.length == 0) {
        return _ref._resolve(REFERENCE_EXPRESSION);
      }
      return result;
    }

    public JavaResolveResult[] resolve(PsiPolyVariantReference ref, boolean incompleteCode) {
      final JavaResolveResult[] result = _resolve((PsiJavaReference)ref, incompleteCode);
      if (result.length > 0 && result[0].getElement() instanceof PsiClass) {
        final PsiType[] parameters = ((PsiJavaCodeReferenceElement)ref).getTypeParameters();
        final JavaResolveResult[] newResult = new JavaResolveResult[result.length];
        for (int i = 0; i < result.length; i++) {
          final CandidateInfo resolveResult = (CandidateInfo)result[i];
          newResult[i] = new CandidateInfo(
            resolveResult,
            ((PsiSubstitutorEx)resolveResult.getSubstitutor()).inplacePutAll((PsiClass)resolveResult.getElement(), parameters)
          );
        }
        return newResult;
      }
      return result;
    }
  }

  private JavaResolveResult[] _resolve(IElementType parentType) {
    if (parentType == null) {
      parentType = getTreeParent() != null ? getTreeParent().getElementType() : null;
    }
    if (parentType == REFERENCE_EXPRESSION) {
      {
        {
          final VariableResolverProcessor processor = new VariableResolverProcessor(this);
          PsiScopesUtil.resolveAndWalk(processor, this, null);
          JavaResolveResult[] result = processor.getResult();

          if (result.length > 0) {
            return processor.getResult();
          }
        }
        {
          final PsiElement classNameElement;
          classNameElement = getReferenceNameElement();
          if (!(classNameElement instanceof PsiIdentifier)) return JavaResolveResult.EMPTY_ARRAY;
          final String className = classNameElement.getText();

          final ClassResolverProcessor processor = new ClassResolverProcessor(className, this);
          PsiScopesUtil.resolveAndWalk(processor, this, null);
          JavaResolveResult[] result = processor.getResult();
          if (result.length > 0) {
            return processor.getResult();
          }
        }
        {
          final String packageName = getCachedTextSkipWhiteSpaceAndComments();
          final PsiManager manager = getManager();
          final PsiPackage aPackage = manager.findPackage(packageName);
          if (aPackage == null) {
            if (!manager.isPartOfPackagePrefix(packageName)) {
              return JavaResolveResult.EMPTY_ARRAY;
            }
            else {
              return CandidateInfo.RESOLVE_RESULT_FOR_PACKAGE_PREFIX_PACKAGE;
            }
          }
          return new JavaResolveResult[]{new CandidateInfo(aPackage, PsiSubstitutor.EMPTY)};
        }
      }
    }
    else if (parentType == METHOD_CALL_EXPRESSION) {
      {
        final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)getParent();
        final MethodResolverProcessor processor = new MethodResolverProcessor(methodCall);
        try {
          PsiScopesUtil.setupAndRunProcessor(processor, methodCall, false);
        }
        catch (MethodProcessorSetupFailedException e) {
          return JavaResolveResult.EMPTY_ARRAY;
        }
        return processor.getResult();
      }
    }
    else {
      {
        final VariableResolverProcessor processor = new VariableResolverProcessor(this);
        PsiScopesUtil.resolveAndWalk(processor, this, null);
        return processor.getResult();
      }
    }
  }

  @NotNull
  public JavaResolveResult[] multiResolve(boolean incompleteCode) {
    final PsiManager manager = getManager();
    if (manager == null) {
      LOG.assertTrue(false, "getManager() == null!");
      return null;
    }

    final ResolveCache resolveCache = ((PsiManagerImpl)manager).getResolveCache();
    return (JavaResolveResult[])resolveCache.resolveWithCaching(this, OurGenericsResolver.INSTANCE, false, incompleteCode);
  }

  public String getCanonicalText() {
    PsiElement element = resolve();
    if (element instanceof PsiClass) return ((PsiClass)element).getQualifiedName();
    return getCachedTextSkipWhiteSpaceAndComments();
  }

  public String getQualifiedName() {
    return getCanonicalText();
  }

  public String getReferenceName() {
    PsiElement element = getReferenceNameElement();
    if (element == null) return null;
    return element.getText();
  }

  public static final @NonNls String LENGTH = "length";

  public PsiType getType() {
    JavaResolveResult result = advancedResolve(false);
    PsiElement resolve = result.getElement();
    if (resolve == null) {
      ASTNode refName = findChildByRole(ChildRole.REFERENCE_NAME);
      if (refName != null && refName.getText().equals(LENGTH)) {
        ASTNode qualifier = findChildByRole(ChildRole.QUALIFIER);
        if (qualifier != null && ElementType.EXPRESSION_BIT_SET.contains(qualifier.getElementType())) {
          PsiType type = ((PsiExpression)SourceTreeToPsiMap.treeElementToPsi(qualifier)).getType();
          if (type instanceof PsiArrayType) {
            return PsiType.INT;
          }
        }
      }
      return null;
    }

    PsiType ret;
    if (resolve instanceof PsiVariable) {
      PsiType type = ((PsiVariable)resolve).getType();
      ret = type instanceof PsiEllipsisType ? ((PsiEllipsisType)type).toArrayType() : type;
    }
    else if (resolve instanceof PsiMethod) {
      ret = ((PsiMethod)resolve).getReturnType();
    }
    else {
      return null;
    }
    if (ret == null) return null;
    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(this);
    if (ret instanceof PsiClassType) {
      ret = ((PsiClassType)ret).setLanguageLevel(languageLevel);
    }

    if (languageLevel.compareTo(LanguageLevel.JDK_1_5) >= 0) {
      PsiType substitutedType = result.getSubstitutor().substitute(ret);
      return PsiImplUtil.normalizeWildcardTypeByPosition(substitutedType, this);
    }

    return TypeConversionUtil.erasure(ret);
  }

  public boolean isReferenceTo(PsiElement element) {
    IElementType i = getLastChildNode().getElementType();
    if (i == IDENTIFIER) {
      {
        if (!(element instanceof PsiPackage)) {
          if (!(element instanceof PsiNamedElement)) return false;
          String name = ((PsiNamedElement)element).getName();
          if (name == null) return false;
          if (!name.equals(getLastChildNode().getText())) return false;
        }
      }
    }
    else if (i == SUPER_KEYWORD || i == THIS_KEYWORD) {
      if (!(element instanceof PsiMethod)) return false;
      if (!((PsiMethod)element).isConstructor()) return false;
    }

    return element.getManager().areElementsEquivalent(element, resolve());
  }


  public Object[] getVariants() {
    //this reference's variants are rather obtained with processVariants()
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isSoft() {
    return false;
  }

  public void processVariants(PsiScopeProcessor processor) {
    OrFilter filter = new OrFilter();
    filter.addFilter(new ClassFilter(PsiClass.class));
    filter.addFilter(new ClassFilter(PsiPackage.class));
    filter.addFilter(new NotFilter(new ConstructorFilter()));
    filter.addFilter(new ClassFilter(PsiVariable.class));

    FilterScopeProcessor proc = new FilterScopeProcessor(filter, this, processor);
    PsiScopesUtil.resolveAndWalk(proc, this, null, true);
  }

  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    final JavaResolveResult[] results = multiResolve(incompleteCode);
    if (results.length == 1) return results[0];
    return JavaResolveResult.EMPTY;
  }

  public PsiElement getReferenceNameElement() {
    return findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
  }

  public PsiReferenceParameterList getParameterList() {
    return (PsiReferenceParameterList)findChildByRoleAsPsiElement(ChildRole.REFERENCE_PARAMETER_LIST);
  }

  public int getTextOffset() {
    ASTNode refName = findChildByRole(ChildRole.REFERENCE_NAME);
    if (refName != null) {
      return refName.getStartOffset();
    }
    else {
      return super.getTextOffset();
    }
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    if (getQualifierExpression() != null) {
      return renameDirectly(newElementName);
    }
    final JavaResolveResult resolveResult = advancedResolve(false);
    if (resolveResult.getElement() == null) {
      return renameDirectly(newElementName);
    }
    PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
    if (!(currentFileResolveScope instanceof PsiImportStaticStatement) ||
        ((PsiImportStaticStatement)currentFileResolveScope).isOnDemand()) {
      return renameDirectly(newElementName);
    }
    final PsiImportStaticStatement importStaticStatement = (PsiImportStaticStatement)currentFileResolveScope;
    final String referenceName = importStaticStatement.getReferenceName();
    LOG.assertTrue(referenceName != null);
    final PsiElement element = importStaticStatement.getImportReference().resolve();
    if (getManager().areElementsEquivalent(element, resolveResult.getElement())) {
      return renameDirectly(newElementName);
    }
    final PsiClass psiClass = importStaticStatement.resolveTargetClass();
    if (psiClass == null) return renameDirectly(newElementName);
    final PsiElementFactory factory = getManager().getElementFactory();
    final PsiReferenceExpression expression = (PsiReferenceExpression)factory.createExpressionFromText("X." + newElementName, this);
    final PsiReferenceExpression result = ((PsiReferenceExpression)this.replace(expression));
      ((PsiReferenceExpression)result.getQualifierExpression()).bindToElement(psiClass);
    return result;
  }

  private PsiElement renameDirectly(String newElementName) throws IncorrectOperationException {
    PsiElement oldIdentifier = findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
    if (oldIdentifier == null) {
      throw new IncorrectOperationException();
    }
    final String oldRefName = oldIdentifier.getText();
    if (PsiKeyword.THIS.equals(oldRefName) || PsiKeyword.SUPER.equals(oldRefName)) return this;
    PsiIdentifier identifier = getManager().getElementFactory().createIdentifier(newElementName);
    oldIdentifier.replace(identifier);
    return this;
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);

    if (isReferenceTo(element)) return this;

    PsiManager manager = getManager();
    if (element instanceof PsiClass) {
      String qName = ((PsiClass)element).getQualifiedName();
      if (qName == null) {
        qName = ((PsiClass)element).getName();
        final PsiClass psiClass = getManager().getResolveHelper().resolveReferencedClass(qName, this);
        if (!getManager().areElementsEquivalent(psiClass, element)) {
          throw new IncorrectOperationException();
        }
      }
      else {
        if (getManager().findClass(qName, getResolveScope()) == null) {
          return this;
        }
      }
      boolean preserveQualification = CodeStyleSettingsManager.getSettings(getProject()).USE_FQ_CLASS_NAMES &&
                                      isFullyQualified(this);
      final CharTable table = SharedImplUtil.findCharTableByTree(getTreeParent());
      TreeElement ref = ExpressionParsing.parseExpressionText(manager, qName.toCharArray(), 0, qName.toCharArray().length, table);
      getTreeParent().replaceChildInternal(this, ref);
      CodeStyleManagerEx codeStyleManager = (CodeStyleManagerEx)manager.getCodeStyleManager();
      if (!preserveQualification) {
        ref = (TreeElement)SourceTreeToPsiMap.psiElementToTree(
          codeStyleManager.shortenClassReferences(SourceTreeToPsiMap.treeElementToPsi(ref), CodeStyleManagerEx.UNCOMPLETE_CODE)
        );
      }
      return SourceTreeToPsiMap.treeElementToPsi(ref);
    }
    else if (element instanceof PsiPackage) {
      String qName = ((PsiPackage)element).getQualifiedName();
      if (qName.length() == 0) {
        throw new IncorrectOperationException();
      }
      final CharTable table = SharedImplUtil.findCharTableByTree(getTreeParent());
      TreeElement ref = ExpressionParsing.parseExpressionText(manager, qName.toCharArray(), 0, qName.length(), table);
      getTreeParent().replaceChildInternal(this, ref);
      return SourceTreeToPsiMap.treeElementToPsi(ref);
    }
    else {
      throw new IncorrectOperationException();
    }
  }

  private static boolean isFullyQualified(CompositeElement classRef) {
    ASTNode qualifier = classRef.findChildByRole(ChildRole.QUALIFIER);
    if (qualifier == null) return false;
    if (qualifier.getElementType() != REFERENCE_EXPRESSION) return false;
    PsiElement refElement = ((PsiReference)qualifier).resolve();
    if (refElement instanceof PsiPackage) return true;
    return isFullyQualified((CompositeElement)qualifier);
  }

  public void deleteChildInternal(ASTNode child) {
    if (getChildRole(child) == ChildRole.QUALIFIER) {
      ASTNode dot = findChildByRole(ChildRole.DOT);
      super.deleteChildInternal(child);
      deleteChildInternal(dot);
    }
    else {
      super.deleteChildInternal(child);
    }
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
    default:
           return null;

    case ChildRole.REFERENCE_NAME:
           return getChildRole(getLastChildNode()) == role ? getLastChildNode() : null;

    case ChildRole.QUALIFIER:
           if (getChildRole(getFirstChildNode()) == ChildRole.QUALIFIER) {
             return getFirstChildNode();
           }
           else {
             return null;
           }

    case ChildRole.REFERENCE_PARAMETER_LIST:
           return TreeUtil.findChild(this, REFERENCE_PARAMETER_LIST);

    case ChildRole.DOT:
           return TreeUtil.findChild(this, DOT);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == DOT) {
      return ChildRole.DOT;
    }
    else if (i == REFERENCE_PARAMETER_LIST) {
      return ChildRole.REFERENCE_PARAMETER_LIST;
    }
    else if (i == IDENTIFIER || i == THIS_KEYWORD || i == SUPER_KEYWORD) {
      return ChildRole.REFERENCE_NAME;
    }
    else {
      if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.QUALIFIER;
      }
      return ChildRole.NONE;
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitReferenceExpression(this);
  }

  public String toString() {
    return "PsiReferenceExpression:" + getText();
  }

  public TextRange getRangeInElement() {
    TreeElement nameChild = (TreeElement)findChildByRole(ChildRole.REFERENCE_NAME);
    return new TextRange(nameChild != null ? nameChild.getStartOffsetInParent() : 0, getTextLength());
  }

  public PsiElement getElement() {
    return this;
  }

  @NotNull
  public PsiType[] getTypeParameters() {
    final PsiReferenceParameterList parameterList = getParameterList();
    if (parameterList == null) return PsiType.EMPTY_ARRAY;
    PsiTypeElement[] typeElements = parameterList.getTypeParameterElements();

    PsiType[] types = new PsiType[typeElements.length];
    for (int i = 0; i < types.length; i++) {
      types[i] = typeElements[i].getType();
    }
    return types;
  }


  public String getClassNameText() {
    if (myCachedQName == null) {
      myCachedQName = PsiNameHelper.getQualifiedClassName(getCachedTextSkipWhiteSpaceAndComments(), false);
    }
    return myCachedQName;
  }

  public void fullyQualify(PsiClass targetClass) {
    SourceUtil.fullyQualifyReference(this, targetClass);
  }

  public boolean isQualified() {
    return getChildRole(getFirstChildNode()) == ChildRole.QUALIFIER;
  }

  public void dequalify() {
    SourceUtil.dequalifyImpl(this);
  }

  private String getCachedTextSkipWhiteSpaceAndComments() {
    if (myCachedTextSkipWhiteSpaceAndComments == null) {
      myCachedTextSkipWhiteSpaceAndComments = SourceUtil.getTextSkipWhiteSpaceAndComments(this);
    }
    return myCachedTextSkipWhiteSpaceAndComments;
  }
}

