package de.plushnikov.intellij.lombok.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.Modifier;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodReceiver;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.ui.RowIcon;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class LombokLightMethodBuilder9Impl extends LightElement implements LombokLightMethodBuilder {
  private final String myName;
  private final LombokLightModifierList myModifierList;
  private final LombokLightParameterListBuilder myParameterList;
  private final LombokLightReferenceListBuilder myThrowsList;

  private PsiType myReturnType;
  private Icon myBaseIcon;
  private PsiClass myContainingClass;
  private boolean myConstructor;
  protected PsiElement myNavigationElement;

  public LombokLightMethodBuilder9Impl(@NotNull PsiManager manager, @NotNull String name) {
    this(manager, StdLanguages.JAVA, name);
  }

  public LombokLightMethodBuilder9Impl(@NotNull PsiManager manager, @NotNull Language language, @NotNull String name) {
    super(manager, language);
    myName = name;
    myParameterList = new LombokLightParameterListBuilder(manager, language, this);
    myModifierList = new LombokLightModifierList(manager, language, this);
    myThrowsList = new LombokLightReferenceListBuilder(manager, language, PsiReferenceList.Role.THROWS_LIST);
    myNavigationElement = this;
  }

  @Override
  public LombokLightMethodBuilder withNavigationElement(PsiElement navigationElement) {
    setNavigationElement(navigationElement);
    return this;
  }

  @Override
  public LombokLightMethodBuilder addModifier(@Modifier @NotNull @NonNls String modifier) {
    myModifierList.addModifier(modifier);
    return this;
  }

  @Override
  public LombokLightMethodBuilder setMethodReturnType(PsiType returnType) {
    myReturnType = returnType;
    if (null != myReturnType) {
      myConstructor = false;
    }
    return this;
  }

  @Override
  public LombokLightMethodBuilder addParameter(@NotNull String name, @NotNull PsiType type) {
    myParameterList.addParameter(new LombokLightParameter(name, type, this, StdLanguages.JAVA));
    return this;
  }

//  public LombokLightMethodBuilder addParameter(@NotNull String name, @NotNull PsiType type, boolean isVarArgs) {
//    if (isVarArgs && !(type instanceof PsiEllipsisType)) {
//      type = new PsiEllipsisType(type);
//    }
//    return addParameter(new LombokLightParameter(name, type, this, StdLanguages.JAVA, isVarArgs));
//  }

  @Override
  public LombokLightMethodBuilder addException(@NotNull PsiClassType type) {
    myThrowsList.addReference(type);
    return this;
  }

  @Override
  public LombokLightMethodBuilder addException(@NotNull String fqName) {
    myThrowsList.addReference(fqName);
    return this;
  }

  @Override
  public LombokLightMethodBuilder setConstructor(boolean constructor) {
    myConstructor = constructor;
    myReturnType = null;
    return this;
  }

  public LombokLightMethodBuilder setBaseIcon(Icon baseIcon) {
    myBaseIcon = baseIcon;
    return this;
  }

  @Override
  public ItemPresentation getPresentation() {
    return JavaPresentationUtil.getMethodPresentation(this);
  }

  public boolean hasTypeParameters() {
    return false;
  }

  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  @Nullable
  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  @Nullable
  public PsiDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
    return false;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Please don't rename light methods");
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  @NotNull
  public PsiModifierList getModifierList() {
    return myModifierList;
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Nullable
  public PsiType getReturnType() {
    return myReturnType;
  }

  @Nullable
  public PsiType getReturnTypeNoResolve() {
    return getReturnType();
  }

  @Nullable
  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  @NotNull
  public PsiParameterList getParameterList() {
    return myParameterList;
  }

  @Override
  public LombokLightMethodBuilder9Impl setNavigationElement(PsiElement navigationElement) {
    myNavigationElement = navigationElement;
    return this;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myNavigationElement;
  }

  @Override
  public LombokLightMethodBuilder setContainingClass(@NotNull PsiClass containingClass) {
    myContainingClass = containingClass;
    return this;
  }

  @NotNull
  public PsiReferenceList getThrowsList() {
    return myThrowsList;
  }

  public PsiCodeBlock getBody() {
    return null;
  }

  public boolean isConstructor() {
    return myConstructor;
  }

  public boolean isVarArgs() {
    return false;
  }

  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  @Override
  public PsiElement getParent() {
    PsiElement result = super.getParent();
    result = null != result ? result : getContainingClass();
    return result;
  }

  @Nullable
  @Override
  public PsiFile getContainingFile() {
    PsiClass containingClass = getContainingClass();
    return containingClass != null ? containingClass.getContainingFile() : null;
  }

  @Override
  public String getText() {
    ASTNode node = getNode();
    if (null != node) {
      return node.getText();
    }
    return "";
  }

  private ASTNode myASTNode;

  @Override
  public ASTNode getNode() {
    if (null == myASTNode) {
      myASTNode = rebuildMethodFromString().getNode();
    }
    return myASTNode;
  }

  private PsiMethod rebuildMethodFromString() {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(((LombokLightModifierList) getModifierList()).getAllModifierProperties());
      PsiType returnType = getReturnType();
      if (null != returnType) {
        builder.append(returnType.getCanonicalText()).append(' ');
      }
      builder.append(getName());
      builder.append('(');
      if (getParameterList().getParametersCount() > 0) {
        for (PsiParameter parameter : getParameterList().getParameters()) {
          builder.append(parameter.getType().getCanonicalText()).append(' ').append(parameter.getName()).append(',');
        }
        builder.deleteCharAt(builder.length() - 1);
      }
      builder.append(')');
      builder.append('{').append("  ").append('}');

      PsiElementFactory elementFactory = JavaPsiFacade.getInstance(getManager().getProject()).getElementFactory();
      return elementFactory.createMethodFromText(builder.toString(), getContainingClass());
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public PsiElement copy() {
    return rebuildMethodFromString();
  }

  public String toString() {
    return "LombokLightMethod: " + getName();
  }

  @NotNull
  public PsiMethod[] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
  }

  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
  }

  public PsiMethod findDeepestSuperMethod() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethod(this);
  }

  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethods(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitMethod(this);
    }
  }

  public Icon getElementIcon(final int flags) {
    Icon methodIcon = myBaseIcon != null ? myBaseIcon :
        hasModifierProperty(PsiModifier.ABSTRACT) ? Icons.ABSTRACT_METHOD_ICON : Icons.METHOD_ICON;
    RowIcon baseIcon = createLayeredIcon(methodIcon, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  protected boolean isVisibilitySupported() {
    return true;
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another);
  }

  @NotNull
  public SearchScope getUseScope() {
    return PsiImplUtil.getMemberUseScope(this);
  }

  @Override
  public PsiElement getContext() {
    final PsiElement navElement = getNavigationElement();
    if (navElement != this) {
      return navElement;
    }

    final PsiClass cls = getContainingClass();
    if (cls != null) {
      return cls;
    }

    return getContainingFile();
  }

  public PsiMethodReceiver getMethodReceiver() {
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LombokLightMethodBuilder9Impl that = (LombokLightMethodBuilder9Impl) o;

    if (myConstructor != that.myConstructor) {
      return false;
    }
    if (myBaseIcon != null ? !myBaseIcon.equals(that.myBaseIcon) : that.myBaseIcon != null) {
      return false;
    }
    if (myContainingClass != null ? !myContainingClass.equals(that.myContainingClass) : that.myContainingClass != null) {
      return false;
    }
    if (!myModifierList.equals(that.myModifierList)) {
      return false;
    }
    if (!myName.equals(that.myName)) {
      return false;
    }
    if (!myParameterList.equals(that.myParameterList)) {
      return false;
    }
    if (myReturnType != null ? !myReturnType.equals(that.myReturnType) : that.myReturnType != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + (myReturnType != null ? myReturnType.hashCode() : 0);
    result = 31 * result + myModifierList.hashCode();
    result = 31 * result + myParameterList.hashCode();
    result = 31 * result + (myBaseIcon != null ? myBaseIcon.hashCode() : 0);
    result = 31 * result + (myContainingClass != null ? myContainingClass.hashCode() : 0);
    result = 31 * result + (myConstructor ? 1 : 0);
    return result;
  }
}
