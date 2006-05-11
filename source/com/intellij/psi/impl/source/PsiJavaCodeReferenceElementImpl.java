package com.intellij.psi.impl.source;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.classes.AnnotationTypeFilter;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiSubstitutorEx;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerEx;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.psi.impl.source.resolve.ClassResolverProcessor;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.VariableResolverProcessor;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class PsiJavaCodeReferenceElementImpl extends CompositePsiElement implements PsiJavaCodeReferenceElement, SourceJavaCodeReference {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl");

  private String myCachedQName = null;
  private String myCachedTextSkipWhiteSpaceAndComments;
  private int myKindWhenDummy = CLASS_NAME_KIND;

  public static final int CLASS_NAME_KIND = 1;
  public static final int PACKAGE_NAME_KIND = 2;
  public static final int CLASS_OR_PACKAGE_NAME_KIND = 3;
  public static final int CLASS_FQ_NAME_KIND = 4;
  public static final int CLASS_FQ_OR_PACKAGE_NAME_KIND = 5;
  public static final int CLASS_IN_QUALIFIED_NEW_KIND = 6;

  public PsiJavaCodeReferenceElementImpl() {
    super(JAVA_CODE_REFERENCE);
  }

  public int getTextOffset() {
    final ASTNode refName = findChildByRole(ChildRole.REFERENCE_NAME);
    if (refName != null) {
      return refName.getStartOffset();
    }
    else {
      return super.getTextOffset();
    }
  }

  public void setKindWhenDummy(final int kind) {
    LOG.assertTrue(getTreeParent().getElementType() == DUMMY_HOLDER);
    myKindWhenDummy = kind;
  }

  public int getKind() {
    IElementType i = getTreeParent().getElementType();
    if (i == DUMMY_HOLDER) {
      return myKindWhenDummy;
    }
    else if (i == TYPE || i == EXTENDS_LIST || i == IMPLEMENTS_LIST || i == EXTENDS_BOUND_LIST || i == THROWS_LIST ||
             i == THIS_EXPRESSION ||
             i == SUPER_EXPRESSION ||
             i == DOC_METHOD_OR_FIELD_REF ||
             i == DOC_TAG_VALUE_TOKEN ||
             i == REFERENCE_PARAMETER_LIST ||
             i == ANNOTATION) {
      return CLASS_NAME_KIND;
    }
    else if (i == NEW_EXPRESSION) {
      final ASTNode qualifier = getTreeParent().findChildByRole(ChildRole.QUALIFIER);
      return qualifier != null ? CLASS_IN_QUALIFIED_NEW_KIND : CLASS_NAME_KIND;
    }
    else if (i == ANONYMOUS_CLASS) {
      if (getTreeParent().getChildRole(this) == ChildRole.BASE_CLASS_REFERENCE) {
        LOG.assertTrue(getTreeParent().getTreeParent().getElementType() == NEW_EXPRESSION);
        final ASTNode qualifier = getTreeParent().getTreeParent().findChildByRole(ChildRole.QUALIFIER);
        return qualifier != null ? CLASS_IN_QUALIFIED_NEW_KIND : CLASS_NAME_KIND;
      }
      else {
        return CLASS_OR_PACKAGE_NAME_KIND; // uncomplete code
      }
    }
    else if (i == PACKAGE_STATEMENT) {
      return PACKAGE_NAME_KIND;
    }
    else if (i == IMPORT_STATEMENT) {
      final boolean isOnDemand = ((PsiImportStatement)SourceTreeToPsiMap.treeElementToPsi(getTreeParent())).isOnDemand();
      return isOnDemand ? CLASS_FQ_OR_PACKAGE_NAME_KIND : CLASS_FQ_NAME_KIND;
    }
    else if (i == IMPORT_STATIC_STATEMENT) {
      return CLASS_FQ_OR_PACKAGE_NAME_KIND;
    }
    else if (i == JAVA_CODE_REFERENCE) {
      final int parentKind = ((PsiJavaCodeReferenceElementImpl)getTreeParent()).getKind();
      switch (parentKind) {
      case CLASS_NAME_KIND:
             return CLASS_OR_PACKAGE_NAME_KIND;

      case PACKAGE_NAME_KIND:
             return PACKAGE_NAME_KIND;

      case CLASS_OR_PACKAGE_NAME_KIND:
             return CLASS_OR_PACKAGE_NAME_KIND;

      case CLASS_FQ_NAME_KIND:
             return CLASS_FQ_OR_PACKAGE_NAME_KIND;

      case CLASS_FQ_OR_PACKAGE_NAME_KIND:
             return CLASS_FQ_OR_PACKAGE_NAME_KIND;

      case CLASS_IN_QUALIFIED_NEW_KIND:
             return CLASS_IN_QUALIFIED_NEW_KIND; //??

      default:
             LOG.assertTrue(false);
             return -1;
      }
    }
    else if (i == CLASS || i == PARAMETER_LIST || i == ERROR_ELEMENT) {
      return CLASS_OR_PACKAGE_NAME_KIND;
    }
    else if (i == IMPORT_STATIC_REFERENCE) {
      return CLASS_FQ_OR_PACKAGE_NAME_KIND;
    }
    else if (i == DOC_TAG || i == DOC_INLINE_TAG || i == DOC_REFERENCE_HOLDER || i == DOC_TYPE_HOLDER) {
      return CLASS_OR_PACKAGE_NAME_KIND;
    }
    else if (i == CODE_FRAGMENT) {
      PsiJavaCodeReferenceCodeFragment fragment = (PsiJavaCodeReferenceCodeFragment)getTreeParent().getPsi();
      return fragment.isClassesAccepted() ? CLASS_FQ_OR_PACKAGE_NAME_KIND : PACKAGE_NAME_KIND;
    }
    else {
      LOG.error("Unknown parent for java code reference:" + getTreeParent());
      return CLASS_NAME_KIND;
    }
  }

  public void deleteChildInternal(final ASTNode child) {
    if (getChildRole(child) == ChildRole.QUALIFIER) {
      final ASTNode dot = findChildByRole(ChildRole.DOT);
      super.deleteChildInternal(child);
      deleteChildInternal(dot);
    }
    else {
      super.deleteChildInternal(child);
    }
  }

  public final ASTNode findChildByRole(final int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
    default:
           return null;

    case ChildRole.REFERENCE_NAME:
           if (getLastChildNode().getElementType() == IDENTIFIER) {
             return getLastChildNode();
           }
           else {
             if (getLastChildNode().getElementType() == REFERENCE_PARAMETER_LIST) {
               ASTNode current = getLastChildNode().getTreePrev();
               while (current != null && WHITE_SPACE_OR_COMMENT_BIT_SET.contains(current.getElementType())) {
                 current = current.getTreePrev();
               }
               if (current != null && current.getElementType() == IDENTIFIER) {
                 return current;
               }
             }
             return null;
           }

    case ChildRole.REFERENCE_PARAMETER_LIST:
           if (getLastChildNode().getElementType() == REFERENCE_PARAMETER_LIST) {
             return getLastChildNode();
           }
           else {
             return null;
           }

    case ChildRole.QUALIFIER:
           if (getFirstChildNode().getElementType() == JAVA_CODE_REFERENCE) {
             return getFirstChildNode();
           }
           else {
             return null;
           }

    case ChildRole.DOT:
           return TreeUtil.findChild(this, DOT);
    }
  }

  public final int getChildRole(final ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    final IElementType i = child.getElementType();
    if (i == REFERENCE_PARAMETER_LIST) {
      return ChildRole.REFERENCE_PARAMETER_LIST;
    }
    else if (i == JAVA_CODE_REFERENCE) {
      return ChildRole.QUALIFIER;
    }
    else if (i == DOT) {
      return ChildRole.DOT;
    }
    else if (i == IDENTIFIER) {
      return ChildRole.REFERENCE_NAME;
    }
    else {
      return ChildRole.NONE;
    }
  }

  /**
     * [dsl]:Should not be called when tree is not loaded
     *
     * @return
     */
  public PsiIdentifier getClassName() {
    return (PsiIdentifier)findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
  }

  public String getCanonicalText() {
    switch (getKind()) {
    case PsiJavaCodeReferenceElementImpl.CLASS_NAME_KIND:
    case PsiJavaCodeReferenceElementImpl.CLASS_OR_PACKAGE_NAME_KIND:
    case PsiJavaCodeReferenceElementImpl.CLASS_IN_QUALIFIED_NEW_KIND:
           {
             final PsiElement target = resolve();
             if (target instanceof PsiClass) {
               final PsiClass aClass = (PsiClass)target;
               String name = aClass.getQualifiedName();
               if (name == null) {
                 name = aClass.getName(); //?
               }
               final PsiType[] types = getTypeParameters();
               if (types.length == 0) return name;

               final StringBuffer buf = new StringBuffer();
               buf.append(name);
               buf.append('<');
               for (int i = 0; i < types.length; i++) {
                 if (i > 0) buf.append(',');
                 buf.append(types[i].getCanonicalText());
               }
               buf.append('>');

               return buf.toString();
             }
             else if (target instanceof PsiPackage) {
               return ((PsiPackage)target).getQualifiedName();
             }
             else {
               LOG.assertTrue(target == null);
               return getTextSkipWhiteSpaceAndComments();
             }
           }

    case PsiJavaCodeReferenceElementImpl.PACKAGE_NAME_KIND:
    case PsiJavaCodeReferenceElementImpl.CLASS_FQ_NAME_KIND:
    case PsiJavaCodeReferenceElementImpl.CLASS_FQ_OR_PACKAGE_NAME_KIND:
           return getTextSkipWhiteSpaceAndComments();

    default:
           LOG.assertTrue(false);
           return null;
    }
  }

  public PsiReference getReference() {
    return this;
  }

  public final PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  private static final class OurGenericsResolver implements ResolveCache.PolyVariantResolver {
    public static final OurGenericsResolver INSTANCE = new OurGenericsResolver();

    public static JavaResolveResult[] _resolve(final PsiJavaReference ref, final boolean incompleteCode) {
      final PsiJavaCodeReferenceElementImpl referenceElement = (PsiJavaCodeReferenceElementImpl)ref;
      final int kind = referenceElement.getKind();
      JavaResolveResult[] result = referenceElement.resolve(kind);
      if (incompleteCode && result.length == 0 && kind != CLASS_FQ_NAME_KIND && kind != CLASS_FQ_OR_PACKAGE_NAME_KIND) {
        final VariableResolverProcessor processor = new VariableResolverProcessor(referenceElement);
        PsiScopesUtil.resolveAndWalk(processor, referenceElement, null, incompleteCode);
        result = processor.getResult();
        if (result.length > 0) {
          return result;
        }
        if (kind == CLASS_NAME_KIND) {
          return referenceElement.resolve(PACKAGE_NAME_KIND);
        }
      }
      return result;
    }

    public JavaResolveResult[] resolve(final PsiPolyVariantReference ref, final boolean incompleteCode) {
      final JavaResolveResult[] result = _resolve((PsiJavaReference)ref, incompleteCode);
      if (result.length > 0 && result[0].getElement() instanceof PsiClass) {
        final PsiType[] parameters = ((PsiJavaCodeReferenceElement)ref).getTypeParameters();
        final JavaResolveResult[] newResult = new JavaResolveResult[result.length];
        for (int i = 0; i < result.length; i++) {
          final CandidateInfo resolveResult = (CandidateInfo)result[i];
          final PsiClass aClass = (PsiClass)resolveResult.getElement();
          assert aClass != null;
          newResult[i] = aClass.getTypeParameters().length == 0 ?
                         resolveResult :
                         new CandidateInfo(
                           resolveResult,
                           resolveResult.getSubstitutor().putAll(aClass, parameters)
                         );
        }
        return newResult;
      }
      return result;
    }
  }

  @NotNull
  public JavaResolveResult advancedResolve(final boolean incompleteCode) {
    final JavaResolveResult[] results = multiResolve(incompleteCode);
    if (results.length == 1) return results[0];
    return JavaResolveResult.EMPTY;
  }

  @NotNull
  public JavaResolveResult[] multiResolve(final boolean incompleteCode) {
    final PsiManager manager = getManager();
    if (manager == null) {
      LOG.assertTrue(false, "getManager() == null!");
      return JavaResolveResult.EMPTY_ARRAY;
    }

    final ResolveCache resolveCache = ((PsiManagerImpl)manager).getResolveCache();
    final boolean needToPreventRecursion = getContext() instanceof PsiReferenceList;
    return (JavaResolveResult[])resolveCache.resolveWithCaching(this, OurGenericsResolver.INSTANCE, needToPreventRecursion, incompleteCode);
  }

  private PsiSubstitutor updateSubstitutor(PsiSubstitutor subst, final PsiClass psiClass) {
    final PsiType[] parameters = getTypeParameters();
    if (psiClass != null) {
      subst = ((PsiSubstitutorEx)subst).inplacePutAll(psiClass, parameters);
    }
    return subst;
  }

  private JavaResolveResult[] resolve(final int kind) {
    switch (kind) {
    case CLASS_FQ_NAME_KIND:
           {
             // TODO: support type parameters in FQ names
             final String textSkipWhiteSpaceAndComments = getTextSkipWhiteSpaceAndComments();
             if (textSkipWhiteSpaceAndComments == null || textSkipWhiteSpaceAndComments.length() == 0) return JavaResolveResult.EMPTY_ARRAY;
             final PsiClass aClass = getManager().findClass(textSkipWhiteSpaceAndComments, getResolveScope());
             if (aClass == null) return JavaResolveResult.EMPTY_ARRAY;
             return new JavaResolveResult[]{new CandidateInfo(aClass, updateSubstitutor(PsiSubstitutor.EMPTY, aClass), this, false)};
           }

    case CLASS_IN_QUALIFIED_NEW_KIND:
           {
             final PsiExpression qualifier;
             PsiElement parent = getParent();
             if (parent instanceof DummyHolder) {
               parent = parent.getContext();
             }

             if (parent instanceof PsiAnonymousClass) {
               parent = parent.getParent();
             }
             if (parent instanceof PsiNewExpression) {
               qualifier = ((PsiNewExpression)parent).getQualifier();
               LOG.assertTrue(qualifier != null);
             }
             else if (parent instanceof PsiJavaCodeReferenceElement) {
               return JavaResolveResult.EMPTY_ARRAY;
             }
             else {
               LOG.assertTrue(false, "Invalid java reference!");
               return JavaResolveResult.EMPTY_ARRAY;
             }

             final PsiType qualifierType = qualifier.getType();
             if (qualifierType == null) return JavaResolveResult.EMPTY_ARRAY;
             if (!(qualifierType instanceof PsiClassType)) return JavaResolveResult.EMPTY_ARRAY;
             final JavaResolveResult result = PsiUtil.resolveGenericsClassInType(qualifierType);
             if (result.getElement() == null) return JavaResolveResult.EMPTY_ARRAY;
             final PsiElement classNameElement;
             classNameElement = getReferenceNameElement();
             if (!(classNameElement instanceof PsiIdentifier)) return JavaResolveResult.EMPTY_ARRAY;
             final String className = classNameElement.getText();

             final ClassResolverProcessor processor = new ClassResolverProcessor(className, this);
             PsiScopesUtil.processScope(result.getElement(), processor, result.getSubstitutor(), this, this);
             return processor.getResult();
           }
    case CLASS_NAME_KIND:
           {
             final PsiElement classNameElement;
             classNameElement = getReferenceNameElement();
             if (!(classNameElement instanceof PsiIdentifier)) return JavaResolveResult.EMPTY_ARRAY;
             final String className = classNameElement.getText();

             final ClassResolverProcessor processor = new ClassResolverProcessor(className, this);
             PsiScopesUtil.resolveAndWalk(processor, this, null);

             return processor.getResult();
           }

    case PACKAGE_NAME_KIND:
           {
             final String packageName = getTextSkipWhiteSpaceAndComments();
             final PsiManager manager = getManager();
             final PsiPackage aPackage = manager.findPackage(packageName);
             if (aPackage == null || !aPackage.isValid()) {
               if (!manager.isPartOfPackagePrefix(packageName)) {
                 return JavaResolveResult.EMPTY_ARRAY;
               }
               else {
                 return CandidateInfo.RESOLVE_RESULT_FOR_PACKAGE_PREFIX_PACKAGE;
               }
             }
             return new JavaResolveResult[]{new CandidateInfo(aPackage, PsiSubstitutor.EMPTY)};
           }

    case CLASS_FQ_OR_PACKAGE_NAME_KIND:
           {
             final JavaResolveResult[] result = resolve(CLASS_FQ_NAME_KIND);
             if (result.length == 0) {
               return resolve(PACKAGE_NAME_KIND);
             }
             return result;
           }
    case CLASS_OR_PACKAGE_NAME_KIND:
           {
             final JavaResolveResult[] classResolveResult = resolve(CLASS_NAME_KIND);
             // [dsl]todo[ik]: review this change I guess ResolveInfo should be merged if both
             // class and package resolve failed.
             if (classResolveResult.length == 0) {
               final JavaResolveResult[] packageResolveResult = resolve(PACKAGE_NAME_KIND);
               if (packageResolveResult.length > 0) return packageResolveResult;
             }
             return classResolveResult;
           }
    default:
           LOG.assertTrue(false);
    }
    return JavaResolveResult.EMPTY_ARRAY;
  }

  public final PsiElement handleElementRename(final String newElementName) throws IncorrectOperationException {
    final PsiElement oldIdentifier = findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
    if (oldIdentifier == null) {
      throw new IncorrectOperationException();
    }
    final PsiIdentifier identifier = getManager().getElementFactory().createIdentifier(newElementName);
    oldIdentifier.replace(identifier);
    return this;
  }

  public PsiElement bindToElement(final PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);

    if (isReferenceTo(element)) return this;

    switch (getKind()) {
      case CLASS_NAME_KIND:
      case CLASS_FQ_NAME_KIND:
        if (!(element instanceof PsiClass)) {
          throw new IncorrectOperationException();
        }
        return bindToClass((PsiClass)element);

      case PACKAGE_NAME_KIND:
        if (!(element instanceof PsiPackage)) {
          throw new IncorrectOperationException();
        }
        return bindToPackage((PsiPackage)element);

      case CLASS_OR_PACKAGE_NAME_KIND:
      case CLASS_FQ_OR_PACKAGE_NAME_KIND:
        if (element instanceof PsiClass) {
          return bindToClass((PsiClass)element);
        }
        else if (element instanceof PsiPackage) {
          return bindToPackage((PsiPackage)element);
        }
        else {
          throw new IncorrectOperationException();
        }

      case CLASS_IN_QUALIFIED_NEW_KIND:
        if (element instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)element;
          final String name = aClass.getName();
          if (name == null) {
            throw new IncorrectOperationException();
          }
          final TreeElement ref =
            Parsing.parseJavaCodeReferenceText(aClass.getManager(), name.toCharArray(), SharedImplUtil.findCharTableByTree(this));
          getTreeParent().replaceChildInternal(this, ref);
          return SourceTreeToPsiMap.treeElementToPsi(ref);
        }
        else {
          throw new IncorrectOperationException();
        }

      default:
        LOG.assertTrue(false);
        return null;
    }
  }

  private PsiElement bindToClass(final PsiClass aClass) throws IncorrectOperationException {
    String qName = aClass.getQualifiedName();
    if (qName == null) {
      qName = aClass.getName();
      final PsiClass psiClass = getManager().getResolveHelper().resolveReferencedClass(qName, this);
      if (!getManager().areElementsEquivalent(psiClass, aClass)) {
        throw new IncorrectOperationException();
      }
    }
    else {
      if (getManager().findClass(qName, getResolveScope()) == null) {
        return this;
      }
    }

    final boolean wasFullyQualified = isFullyQualified();
    final PsiManager manager = aClass.getManager();
    ASTNode ref =
    Parsing.parseJavaCodeReferenceText(manager, (qName + getParameterList().getText()).toCharArray(),
                                       SharedImplUtil.findCharTableByTree(this));
    getTreeParent().replaceChildInternal(this, (TreeElement)ref);
    if (!wasFullyQualified /*&& (TreeUtil.findParent(ref, ElementType.DOC_COMMENT) == null)*/) {
      final CodeStyleManagerEx codeStyleManager = (CodeStyleManagerEx)manager.getCodeStyleManager();
      ref = SourceTreeToPsiMap.psiElementToTree(
        codeStyleManager.shortenClassReferences(SourceTreeToPsiMap.treeElementToPsi(ref), CodeStyleManagerEx.UNCOMPLETE_CODE)
      );
    }
    return SourceTreeToPsiMap.treeElementToPsi(ref);
  }

  private boolean isFullyQualified() {
    switch (getKind()) {
    case CLASS_OR_PACKAGE_NAME_KIND:
           if (resolve() instanceof PsiPackage) return true;
    case CLASS_NAME_KIND:
           break;

    case PACKAGE_NAME_KIND:
    case CLASS_FQ_NAME_KIND:
    case CLASS_FQ_OR_PACKAGE_NAME_KIND:
           return true;

    default:
           LOG.assertTrue(false);
           return true;
    }

    final ASTNode qualifier = findChildByRole(ChildRole.QUALIFIER);
    if (qualifier == null) return false;

    LOG.assertTrue(qualifier.getElementType() == JAVA_CODE_REFERENCE);
    final PsiElement refElement = ((PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(qualifier)).resolve();
    if (refElement instanceof PsiPackage) return true;

    return ((PsiJavaCodeReferenceElementImpl)SourceTreeToPsiMap.treeElementToPsi(qualifier)).isFullyQualified();
  }

  private PsiElement bindToPackage(final PsiPackage aPackage) throws IncorrectOperationException {
    final String qName = aPackage.getQualifiedName();
    if (qName.length() == 0) {
      throw new IncorrectOperationException();
    }
    final TreeElement ref = Parsing.parseJavaCodeReferenceText(getManager(), qName.toCharArray(), SharedImplUtil.findCharTableByTree(this));
    getTreeParent().replaceChildInternal(this, ref);
    return SourceTreeToPsiMap.treeElementToPsi(ref);
  }

  public boolean isReferenceTo(final PsiElement element) {
    switch (getKind()) {
    case CLASS_NAME_KIND:
    case CLASS_IN_QUALIFIED_NEW_KIND:
           if (!(element instanceof PsiClass)) return false;
           break;

    case CLASS_FQ_NAME_KIND:
           {
             if (!(element instanceof PsiClass)) return false;
             final String qName = ((PsiClass)element).getQualifiedName();
             if (qName == null) return false;
             return qName.equals(getCanonicalText());
           }

    case PACKAGE_NAME_KIND:
           {
             if (!(element instanceof PsiPackage)) return false;
             final String qName = ((PsiPackage)element).getQualifiedName();
             return qName.equals(getCanonicalText());
           }

    case CLASS_OR_PACKAGE_NAME_KIND:
           {
             //        if (lastChild.type != IDENTIFIER) return false;
             if (element instanceof PsiPackage) {
               final String qName = ((PsiPackage)element).getQualifiedName();
               return qName.equals(getCanonicalText());
             }
             else if (element instanceof PsiClass) {
               final PsiIdentifier nameIdentifier = ((PsiClass)element).getNameIdentifier();
               if (nameIdentifier == null) return false;
               if (!getReferenceNameElement().textMatches(nameIdentifier)) return false;
               return element.getManager().areElementsEquivalent(element, resolve());
             }
             else {
               return false;
             }
           }

    case CLASS_FQ_OR_PACKAGE_NAME_KIND:
           if (element instanceof PsiClass) {
             final String qName = ((PsiClass)element).getQualifiedName();
             if (qName == null) return false;
             return qName.equals(getCanonicalText());
           }
           else if (element instanceof PsiPackage) {
             final String qName = ((PsiPackage)element).getQualifiedName();
             return qName.equals(getCanonicalText());
           }
           else {
             return false;
           }

    default:
           LOG.assertTrue(false);
           return true;
    }

    final ASTNode referenceNameElement = findChildByRole(ChildRole.REFERENCE_NAME);
    if (referenceNameElement.getElementType() != IDENTIFIER) return false;
    final String name = ((PsiClass)element).getName();
    if (name == null) return false;
    if (!referenceNameElement.getText().equals(name)) return false;
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  private String getTextSkipWhiteSpaceAndComments() {
    if (myCachedTextSkipWhiteSpaceAndComments == null) {
      myCachedTextSkipWhiteSpaceAndComments = SourceUtil.getTextSkipWhiteSpaceAndComments(this);
    }
    return myCachedTextSkipWhiteSpaceAndComments;
  }

  public String getClassNameText() {
    if (myCachedQName == null) {
      myCachedQName = PsiNameHelper.getQualifiedClassName(getTextSkipWhiteSpaceAndComments(), false);
    }
    return myCachedQName;
  }

  public void fullyQualify(final PsiClass targetClass) {
    final int kind = getKind();
    if (kind != CLASS_NAME_KIND && kind != CLASS_OR_PACKAGE_NAME_KIND && kind != CLASS_IN_QUALIFIED_NEW_KIND) {
      LOG.error("Wrong kind " + kind);
      return;
    }
    SourceUtil.fullyQualifyReference(this, targetClass);
  }

  public boolean isQualified() {
    return getChildRole(getFirstChildNode()) != ChildRole.REFERENCE_NAME;
  }

  public PsiElement getQualifier() {
    return SourceTreeToPsiMap.treeElementToPsi(findChildByRole(ChildRole.QUALIFIER));
  }

  public void dequalify() {
    SourceUtil.dequalifyImpl(this);
  }

  public void clearCaches() {
    super.clearCaches();
    myCachedQName = null;
    myCachedTextSkipWhiteSpaceAndComments = null;
  }

  public Object[] getVariants() {
    final ElementFilter filter;
    switch (getKind()) {

    case CLASS_OR_PACKAGE_NAME_KIND:
           filter = new OrFilter();
             ((OrFilter)filter).addFilter(new ClassFilter(PsiClass.class));
             ((OrFilter)filter).addFilter(new ClassFilter(PsiPackage.class));
           break;
    case CLASS_NAME_KIND:
           filter = new ClassFilter(PsiClass.class);
           break;
    case PACKAGE_NAME_KIND:
           filter = new ClassFilter(PsiPackage.class);
           break;
    case CLASS_FQ_NAME_KIND:
    case CLASS_FQ_OR_PACKAGE_NAME_KIND:
           filter = new OrFilter();
             ((OrFilter)filter).addFilter(new ClassFilter(PsiPackage.class));
           if (isQualified()) {
               ((OrFilter)filter).addFilter(new ClassFilter(PsiClass.class));
           }
           break;
    case CLASS_IN_QUALIFIED_NEW_KIND:
           filter = new ClassFilter(PsiClass.class);
           break;
    default:
           throw new RuntimeException("Unknown reference type");
    }

    return PsiImplUtil.getReferenceVariantsByFilter(this, filter);
  }

  public boolean isSoft() {
    return false;
  }

  public void processVariants(final PsiScopeProcessor processor) {
    final OrFilter filter = new OrFilter();
    PsiElement superParent = getParent();
    boolean smartCompletion = true;
    if (isQualified()) {
      smartCompletion = false;
    }
    else {
      while (superParent != null) {
        if (superParent instanceof PsiCodeBlock || superParent instanceof PsiVariable) {
          smartCompletion = false;
          break;
        }
        superParent = superParent.getParent();
      }
    }
    if (!smartCompletion) {
      /*filter.addFilter(new ClassFilter(PsiClass.class));
      filter.addFilter(new ClassFilter(PsiPackage.class));*/
      filter.addFilter(new NotFilter(new ConstructorFilter()));
      filter.addFilter(new ClassFilter(PsiVariable.class));
    }
    switch (getKind()) {
    case CLASS_OR_PACKAGE_NAME_KIND:
           filter.addFilter(new ClassFilter(PsiClass.class));
           filter.addFilter(new ClassFilter(PsiPackage.class));
           break;
    case CLASS_NAME_KIND:
           if (getParent() instanceof PsiAnnotation) {
             filter.addFilter(new AnnotationTypeFilter());
           }
           else {
             filter.addFilter(new ClassFilter(PsiClass.class));
           }
           break;
    case PACKAGE_NAME_KIND:
           filter.addFilter(new ClassFilter(PsiPackage.class));
           break;
    case CLASS_FQ_NAME_KIND:
    case CLASS_FQ_OR_PACKAGE_NAME_KIND:
           filter.addFilter(new ClassFilter(PsiPackage.class));
           if (isQualified()) {
             filter.addFilter(new ClassFilter(PsiClass.class));
           }
           break;
    case CLASS_IN_QUALIFIED_NEW_KIND:
           final PsiElement parent = getParent();
           if (parent instanceof PsiNewExpression) {
             final PsiNewExpression newExpr = (PsiNewExpression)parent;
             final PsiType type = newExpr.getQualifier().getType();
             final PsiClass aClass = PsiUtil.resolveClassInType(type);
             if (aClass != null) {
               PsiScopesUtil.processScope(
                 aClass,
                 new FilterScopeProcessor(new AndFilter(new ClassFilter(PsiClass.class), new ModifierFilter(PsiModifier.STATIC, false)),
                                          this,
                                          processor),
                 PsiSubstitutor.EMPTY,
                 null,
                 this
               );
             }
             //          else{
             //            throw new RuntimeException("Qualified new is not allowed for primitives");
             //          }
           }
           //        else{
           //          throw new RuntimeException("Reference type is qualified new, but parent expression is: " + getParent());
           //        }

           return;
    default:
           throw new RuntimeException("Unknown reference type");
    }
    final FilterScopeProcessor proc = new FilterScopeProcessor(filter, this, processor);
    PsiScopesUtil.resolveAndWalk(proc, this, null, true);
  }

  public PsiElement getReferenceNameElement() {
    return findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
  }

  public PsiReferenceParameterList getParameterList() {
    return (PsiReferenceParameterList)findChildByRoleAsPsiElement(ChildRole.REFERENCE_PARAMETER_LIST);
  }

  public String getQualifiedName() {
    switch (getKind()) {
    case CLASS_NAME_KIND:
    case CLASS_OR_PACKAGE_NAME_KIND:
    case CLASS_IN_QUALIFIED_NEW_KIND:
           {
             final PsiElement target = resolve();
             if (target instanceof PsiClass) {
               final PsiClass aClass = (PsiClass)target;
               String name = aClass.getQualifiedName();
               if (name == null) {
                 name = aClass.getName(); //?
               }
               return name;
             }
             else if (target instanceof PsiPackage) {
               return ((PsiPackage)target).getQualifiedName();
             }
             else {
               LOG.assertTrue(target == null);
               return getClassNameText();
             }
           }

    case PACKAGE_NAME_KIND:
    case CLASS_FQ_NAME_KIND:
    case CLASS_FQ_OR_PACKAGE_NAME_KIND:
           return getTextSkipWhiteSpaceAndComments(); // there cannot be any <...>

    default:
           LOG.assertTrue(false);
           return null;
    }

  }

  public String getReferenceName() {
    final ASTNode childByRole = findChildByRole(ChildRole.REFERENCE_NAME);
    if (childByRole == null) return null;
    return childByRole.getText();
  }

  public final TextRange getRangeInElement() {
    final TreeElement nameChild = (TreeElement)findChildByRole(ChildRole.REFERENCE_NAME);
    if (nameChild == null) return new TextRange(0, getTextLength());
    final int startOffset = nameChild.getStartOffsetInParent();
    return new TextRange(startOffset, startOffset + nameChild.getTextLength());
  }

  @NotNull
  public PsiType[] getTypeParameters() {
    final PsiReferenceParameterList parameterList = getParameterList();
    if (parameterList == null) return PsiType.EMPTY_ARRAY;
    return parameterList.getTypeArguments();

  }

  public final PsiElement getElement() {
    return this;
  }

  public final void accept(final PsiElementVisitor visitor) {
    visitor.visitReferenceElement(this);
  }

  public final String toString() {
    return "PsiJavaCodeReferenceElement:" + getText();
  }
}
