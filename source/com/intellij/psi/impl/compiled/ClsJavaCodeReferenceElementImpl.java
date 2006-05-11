package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiSubstitutorImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.text.CharacterIterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

public class ClsJavaCodeReferenceElementImpl extends ClsElementImpl implements PsiJavaCodeReferenceElement {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.psi.impl.compiled.ClsJavaCodeReferenceElementImpl");

  private PsiElement myParent;
  private final String myCanonicalText;
  private final String myQualifiedName;
  private ClsReferenceParametersListImpl myTypeParameterList = null;
  private final ClsTypeElementImpl[] myTypeParameters;  // in right-to-left order
  private PsiType[] myTypeParametersCachedTypes = null; // in left-to-right-order
  private static final @NonNls String EXTENDS_PREFIX = "?extends";
  private static final @NonNls String SUPER_PREFIX = "?super";

  public ClsJavaCodeReferenceElementImpl(PsiElement parent, String canonicalText) {
    myParent = parent;

    myCanonicalText = canonicalText;
    final String[] classParametersText = PsiNameHelper.getClassParametersText(canonicalText);
    myTypeParameters = new ClsTypeElementImpl[classParametersText.length];
    for (int i = 0; i < classParametersText.length; i++) {
      String s = classParametersText[classParametersText.length - i - 1];
      char variance = ClsTypeElementImpl.VARIANCE_NONE;
      if (s.startsWith(EXTENDS_PREFIX)) {
        variance = ClsTypeElementImpl.VARIANCE_EXTENDS;
        s = s.substring(EXTENDS_PREFIX.length());
      }
      else if (s.startsWith(SUPER_PREFIX)) {
        variance = ClsTypeElementImpl.VARIANCE_SUPER;
        s = s.substring(SUPER_PREFIX.length());
      }
      else if (StringUtil.startsWithChar(s, '?')) {
        variance = ClsTypeElementImpl.VARIANCE_INVARIANT;
        s = s.substring(1);
      }

      myTypeParameters[i] = new ClsTypeElementImpl(this, s, variance);
    }

    myQualifiedName = PsiNameHelper.getQualifiedClassName(myCanonicalText, false);
  }

  public ClsJavaCodeReferenceElementImpl(PsiElement psiParent, CharacterIterator signature) throws ClsFormatException {
    myParent = psiParent;
    StringBuffer canonicalText = new StringBuffer();
    LOG.assertTrue(signature.current() == 'L');
    signature.next();
    ArrayList<ClsTypeElementImpl> typeParameters = new ArrayList<ClsTypeElementImpl>();
    while (signature.current() != ';' && signature.current() != CharacterIterator.DONE) {
      switch (signature.current()) {
        case '$':
        case '/':
        case '.':
          canonicalText.append('.');
          break;
        case '<':
          canonicalText.append('<');
          signature.next();
          do {
            processTypeArgument(signature, typeParameters, canonicalText);
          }
          while (signature.current() != '>');
          canonicalText.append('>');
          break;
        case ' ':
          break;
        default:
          canonicalText.append(signature.current());
      }
      signature.next();
    }

    if (signature.current() == CharacterIterator.DONE) {
      throw new ClsFormatException();
    }

    for(int index = 0; index < canonicalText.length(); index++) {
      final char c = canonicalText.charAt(index);
      if ('0' <= c && c <= '1') {
        if (index > 0 && canonicalText.charAt(index-1) == '.') {
          canonicalText.setCharAt(index-1, '$');
        }
      }
    }
    myCanonicalText = canonicalText.toString();
    final int nParams = typeParameters.size();
    myTypeParameters = new ClsTypeElementImpl[nParams];
    for (int i = nParams - 1; i >= 0; i--) {
      myTypeParameters[nParams - i - 1] = typeParameters.get(i);
    }
    myQualifiedName = PsiNameHelper.getQualifiedClassName(myCanonicalText, false);

    signature.next();
  }

  private void processTypeArgument(CharacterIterator signature, ArrayList<ClsTypeElementImpl> typeParameters, StringBuffer canonicalText)
    throws ClsFormatException {
    ClsTypeElementImpl typeArgument = GenericSignatureParsing.parseClassOrTypeVariableElement(signature, this);
    typeParameters.add(typeArgument);
    canonicalText.append(typeArgument.getCanonicalText());
    if (signature.current() != '>') {
      canonicalText.append(',');
    }
  }


  void setParent(PsiElement parent) {
    myParent = parent;
  }

  @NotNull
  public PsiElement[] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiElement getParent() {
    return myParent;
  }

  public String getText() {
    return PsiNameHelper.getPresentableText(this);
  }

  public int getTextLength() {
    return getText().length();
  }

  public PsiReference getReference() {
    return this;
  }

  public String getCanonicalText() {
    return myCanonicalText;
  }

  private static class Resolver implements ResolveCache.PolyVariantResolver {
    public static Resolver INSTANCE = new Resolver();

    public JavaResolveResult[] resolve(PsiPolyVariantReference ref, boolean incompleteCode) {
      final JavaResolveResult resolveResult = ((ClsJavaCodeReferenceElementImpl)ref).advancedResolveImpl();
      return resolveResult.getElement() == null ? JavaResolveResult.EMPTY_ARRAY : new JavaResolveResult[] {resolveResult};
    }
  }

  private JavaResolveResult advancedResolveImpl() {
    final PsiElement resolve = resolveElement();
    if (resolve instanceof PsiClass) {
      final Map<PsiTypeParameter, PsiType> substitutionMap = new HashMap<PsiTypeParameter, PsiType>();
      final Iterator<PsiTypeParameter> it = PsiUtil.typeParametersIterator((PsiClass)resolve);
      int index = 0;
      while (it.hasNext()) {
        PsiTypeParameter parameter = it.next();
        if (index >= myTypeParameters.length) {
          substitutionMap.put(parameter, null);
        }
        else {
          substitutionMap.put(parameter, myTypeParameters[index].getType());
        }
        index++;
      }
      return new CandidateInfo(resolve, PsiSubstitutorImpl.createSubstitutor(substitutionMap));
    }
    else {
      return new CandidateInfo(resolve, PsiSubstitutor.EMPTY);
    }
  }


  @NotNull
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    final JavaResolveResult[] results = multiResolve(incompleteCode);
    if (results.length == 1) return results[0];
    return JavaResolveResult.EMPTY;
  }

  @NotNull
  public JavaResolveResult[] multiResolve(boolean incompleteCode) {
    final ResolveCache resolveCache = ((PsiManagerImpl)getManager()).getResolveCache();
    return (JavaResolveResult[])resolveCache.resolveWithCaching(this, Resolver.INSTANCE, false, incompleteCode);
  }

  public PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  private PsiElement resolveElement() {
    PsiElement element = getParent();
    while(!(element instanceof PsiClass) || element instanceof PsiTypeParameter){
      if(element instanceof PsiMethod){
        final PsiMethod method = (PsiMethod)element;
        final PsiTypeParameterList list = method.getTypeParameterList();
        if (list != null) {
          final PsiTypeParameter[] parameters = list.getTypeParameters();
          for (int i = 0; parameters != null && i < parameters.length; i++) {
            final PsiTypeParameter parameter = parameters[i];
            if (parameter.getName().equals(myQualifiedName)) return parameter;
          }
        }
      }
      element = element.getParent();
    }

    Iterator<PsiTypeParameter> it = PsiUtil.typeParametersIterator((PsiClass)element);
    while (it.hasNext()) {
      PsiTypeParameter parameter = it.next();
      if (parameter.getName().equals(myQualifiedName)) return parameter;
    }
    return getManager().findClass(myQualifiedName, getResolveScope());
  }

  public void processVariants(PsiScopeProcessor processor) {
    throw new RuntimeException("Variants are not available for light references");
  }

  public PsiElement getReferenceNameElement() {
    return null;
  }

  public PsiReferenceParameterList getParameterList() {
    return myTypeParameterList;
  }

  public String getQualifiedName() {
    return getCanonicalText();
  }

  public String getReferenceName() {
    return PsiNameHelper.getShortClassName(myCanonicalText);
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof PsiClass)) return false;
    PsiClass aClass = (PsiClass)element;
    return myCanonicalText.equals(aClass.getQualifiedName());
  }

  public Object[] getVariants() {
    throw new RuntimeException("Variants are not available for references to compiled code");
  }

  public boolean isSoft() {
    return false;
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    buffer.append(getCanonicalText());
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(myMirror == null);
    LOG.assertTrue(element.getElementType() == ElementType.JAVA_CODE_REFERENCE);
    myMirror = element;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitReferenceElement(this);
  }

  public String toString() {
    return "PsiJavaCodeReferenceElement:" + getText();
  }

  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  public PsiElement getElement() {
    return this;
  }

  ClsTypeElementImpl[] getTypeElements() {
    return myTypeParameters;
  }

  @NotNull
  public PsiType[] getTypeParameters() {
    if (myTypeParametersCachedTypes == null) {
      PsiType[] types = new PsiType[myTypeParameters.length];
      for (int i = 0; i < types.length; i++) {
        types[types.length - i - 1] = myTypeParameters[i].getType();
      }
      myTypeParametersCachedTypes = types;
    }

    return myTypeParametersCachedTypes;
  }

  public boolean isQualified() {
    return false;
  }

  public PsiElement getQualifier() {
    return null;
  }

}
