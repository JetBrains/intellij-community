package com.intellij.psi.impl.source;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public class PsiImportStaticReferenceElementImpl extends CompositePsiElement implements PsiImportStaticReferenceElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiImportStaticReferenceElementImpl");
  private String myCanonicalText;

  public PsiImportStaticReferenceElementImpl() {
    super(IMPORT_STATIC_REFERENCE);
  }

  public int getTextOffset() {
    TreeElement refName = findChildByRole(ChildRole.REFERENCE_NAME);
    if (refName != null){
      return refName.getStartOffset();
    }
    else{
      return super.getTextOffset();
    }
  }

  public void clearCaches() {
    super.clearCaches();
    myCanonicalText = null;
  }

  public final TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.REFERENCE_NAME:
        if (lastChild.getElementType() == IDENTIFIER){
          return lastChild;
        }
        else{
          return null;
        }

      case ChildRole.QUALIFIER:
        if (firstChild.getElementType() == JAVA_CODE_REFERENCE){
          return firstChild;
        }
        else{
          return null;
        }

      case ChildRole.DOT:
        return TreeUtil.findChild(this, DOT);
    }
  }

  public final int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JAVA_CODE_REFERENCE) {
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


  public PsiElement getReferenceNameElement() {
    return findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
  }

  public PsiReferenceParameterList getParameterList() {
    return null;
  }

  public PsiType[] getTypeParameters() {
    return PsiType.EMPTY_ARRAY;
  }

  public PsiElement getQualifier() {
    return findChildByRoleAsPsiElement(ChildRole.QUALIFIER);
  }

  public PsiJavaCodeReferenceElement getClassReference() {
    return (PsiJavaCodeReferenceElement)findChildByRoleAsPsiElement(ChildRole.QUALIFIER);
  }

  public PsiImportStaticStatement bindToTargetClass(PsiClass aClass) throws IncorrectOperationException {
    final String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) throw new IncorrectOperationException();
    final CompositeElement newRef = Parsing.parseJavaCodeReferenceText(getManager(), qualifiedName.toCharArray(), SharedImplUtil.findCharTableByTree(this));
    if (getQualifier() != null) {
      replaceChildInternal(findChildByRole(ChildRole.QUALIFIER), newRef);
      return (PsiImportStaticStatement)getParent();
    }
    else {
      final LeafElement dot = Factory.createSingleLeafElement(ElementType.DOT, new char[]{'.'}, 0, 1, SharedImplUtil.findCharTableByTree(newRef), getManager());
      TreeUtil.insertAfter(newRef, dot);
      final CompositeElement errorElement = Factory.createErrorElement("Identifier or '*' expected");
      TreeUtil.insertAfter(dot, errorElement);
      final CompositeElement parentComposite = ((CompositeElement)SourceTreeToPsiMap.psiElementToTree(getParent()));
      parentComposite.addInternal(newRef, errorElement, this, Boolean.TRUE);
      parentComposite.deleteChildInternal(this);
      return (PsiImportStaticStatement)SourceTreeToPsiMap.treeElementToPsi(parentComposite);
    }
  }

  public boolean isQualified() {
    return findChildByRole(ChildRole.QUALIFIER) != null;
  }

  public String getQualifiedName() {
    return getCanonicalText();
  }

  public boolean isSoft() {
    return false;
  }

  public String getReferenceName() {
    final TreeElement childByRole = findChildByRole(ChildRole.REFERENCE_NAME);
    if (childByRole == null) return "";
    return childByRole.getText();
  }

  public PsiElement getElement() {
    return this;
  }

  public TextRange getRangeInElement() {
    TreeElement nameChild = findChildByRole(ChildRole.REFERENCE_NAME);
    if (nameChild == null) return new TextRange(0, getTextLength());
    final int startOffset = nameChild.getStartOffsetInParent();
    return new TextRange(startOffset, startOffset + nameChild.getTextLength());
  }

  public String getCanonicalText() {
    if (myCanonicalText == null) {
      myCanonicalText = calcCanonicalText();
    }
    return myCanonicalText;
  }

  private String calcCanonicalText() {
    final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)getQualifier();
    if (referenceElement == null) {
      return getReferenceName();
    }
    else {
      return referenceElement.getCanonicalText() + "." + getReferenceName();
    }
  }

  public String toString() {
    return "PsiImportStaticReferenceElement:" + getText();
  }

  public ResolveResult advancedResolve(boolean incompleteCode) {
    final ResolveResult[] results = multiResolve(incompleteCode);
    if (results.length == 1) return results[0];
    return ResolveResult.EMPTY;
  }

  public ResolveResult[] multiResolve(boolean incompleteCode) {
    final ResolveCache resolveCache = ((PsiManagerImpl)getManager()).getResolveCache();
    return resolveCache.resolveWithCaching(this, OurGenericsResolver.INSTANCE, false, incompleteCode);
  }

  private class OurResolveResult implements ResolveResult {
    PsiMember myTarget;
    Boolean myAccessible = null;


    public OurResolveResult(PsiMember target) {
      myTarget = target;
    }

    public PsiMember getElement() {
      return myTarget;
    }

    public PsiSubstitutor getSubstitutor() {
      return PsiSubstitutor.EMPTY;
    }

    public boolean isValidResult() {
      return isAccessible();
    }

    public boolean isAccessible() {
      if (myAccessible == null) {
        myAccessible = Boolean.valueOf(getManager().getResolveHelper().isAccessible(myTarget, PsiImportStaticReferenceElementImpl.this, null));
      }
      return myAccessible.booleanValue();
    }

    public boolean isStaticsScopeCorrect() {
      return true;
    }

    public PsiElement getCurrentFileResolveScope() {
      return null;
    }

    public boolean isPackagePrefixPackageReference() {
      return false;
    }

  }

  private static final class OurGenericsResolver implements ResolveCache.GenericsResolver {
    private static final OurGenericsResolver INSTANCE = new OurGenericsResolver();
    public ResolveResult[] resolve(PsiJavaReference ref, boolean incompleteCode) {
      LOG.assertTrue(ref instanceof PsiImportStaticReferenceElementImpl);
      final PsiImportStaticReferenceElementImpl referenceElement = ((PsiImportStaticReferenceElementImpl)ref);
      final PsiElement qualifier = referenceElement.getQualifier();
      if (!(qualifier instanceof PsiJavaCodeReferenceElement)) return ResolveResult.EMPTY_ARRAY;
      final PsiElement target = ((PsiJavaCodeReferenceElement)qualifier).resolve();
      if (!(target instanceof PsiClass)) return ResolveResult.EMPTY_ARRAY;
      final ArrayList<ResolveResult> results = new ArrayList<ResolveResult>();
      target.processDeclarations(referenceElement.new MyScopeProcessor(results),
                                 PsiSubstitutor.EMPTY, referenceElement, referenceElement);
      if (results.size() <= 1) {
        return results.toArray(new ResolveResult[results.size()]);
      }
      for(int i = results.size() - 1; i >= 0; i--) {
        final ResolveResult resolveResult = results.get(i);
        if (!resolveResult.isValidResult()) {
          results.remove(i);
        }
      }
      return results.toArray(new ResolveResult[results.size()]);
    }

  }

  private class MyScopeProcessor extends BaseScopeProcessor implements NameHint {
    private final List<ResolveResult> myResults;

    public MyScopeProcessor(List<ResolveResult> results) {
      myResults = results;
    }

    public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
      if (element instanceof PsiMember
          && ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) {
        myResults.add(new OurResolveResult((PsiMember)element));
      }
      return true;
    }

    public void handleEvent(Event event, Object associated) {
    }

    public String getName() {
      return getReferenceName();
    }
  }

  public PsiReference getReference() {
    return this;
  }

  public PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  public boolean isReferenceTo(PsiElement element) {
    final String name = getReferenceName();
    if (name == null) return false;
    if (!(element instanceof PsiNamedElement) || !(name.equals(((PsiNamedElement)element).getName()))) {
      return false;
    }
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    PsiElement oldIdentifier = findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
    if (oldIdentifier == null){
      throw new IncorrectOperationException();
    }
    PsiIdentifier identifier = getManager().getElementFactory().createIdentifier(newElementName);
    oldIdentifier.replace(identifier);
    return this;
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiMember) ||
        !(element instanceof PsiNamedElement) ||
        !((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC) ||
        ((PsiNamedElement)element).getName() == null) {
      throw new IncorrectOperationException();
    }

    PsiClass containingClass = ((PsiMember)element).getContainingClass();
    if (containingClass == null) throw new IncorrectOperationException();
    PsiElement qualifier = getQualifier();
    if (qualifier == null) {
      throw new IncorrectOperationException();
    } else {
      ((PsiJavaCodeReferenceElement)qualifier).bindToElement(containingClass);
    }

    PsiElement oldIdentifier = findChildByRoleAsPsiElement(ChildRole.REFERENCE_NAME);
    if (oldIdentifier == null){
      throw new IncorrectOperationException();
    }

    PsiIdentifier identifier = getManager().getElementFactory().createIdentifier(((PsiNamedElement)element).getName());
    oldIdentifier.replace(identifier);
    return this;
  }

  public void processVariants(PsiScopeProcessor processor) {
    ElementFilter filter = new OrFilter(new ClassFilter[]{new ClassFilter(PsiModifierListOwner.class),
                                                          new ClassFilter(PsiPackage.class)});
    FilterScopeProcessor proc = new FilterScopeProcessor(filter, this, processor);
    PsiScopesUtil.resolveAndWalk(proc, this, null, true);
  }

  public Object[] getVariants() {
    // IMPLEMENT[dsl]
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitImportStaticReferenceElement(this);
  }
}
