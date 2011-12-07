package de.plushnikov.intellij.lombok.psi;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.Modifier;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.util.StringBuilderSpinAllocator;

/**
 * @author Plushnikov Michail
 */
public class LombokLightMethodBuilder10Impl extends LightMethodBuilder implements LombokLightMethodBuilder {
  private       ASTNode                               myASTNode;
  private final LombokLightReferenceListBuilder10Impl myThrowsList;

  public LombokLightMethodBuilder10Impl(@NotNull PsiManager manager, @NotNull String name) {
    super(manager, name);

    myThrowsList = new LombokLightReferenceListBuilder10Impl(manager, PsiReferenceList.Role.THROWS_LIST);
  }

  @Override
  public LombokLightMethodBuilder withNavigationElement(PsiElement navigationElement) {
    setNavigationElement(navigationElement);
    return this;
  }

  @Override
  public LombokLightMethodBuilder withModifier(@Modifier @NotNull @NonNls String modifier) {
    addModifier(modifier);
    return this;
  }

  @Override
  public LombokLightMethodBuilder withMethodReturnType(PsiType returnType) {
    setReturnType(returnType);
    return this;
  }

  @Override
  public LombokLightMethodBuilder withParameter(@NotNull String name, @NotNull PsiType type) {
    addParameter(name, type);
    return this;
  }

  @Override
  public LombokLightMethodBuilder withException(@NotNull PsiClassType type) {
    myThrowsList.addReference(type);
    return this;
  }

  @Override
  public LombokLightMethodBuilder withException(@NotNull String fqName) {
    myThrowsList.addReference(fqName);
    return this;
  }

  @Override
  public LombokLightMethodBuilder withContainingClass(@NotNull PsiClass containingClass) {
    setContainingClass(containingClass);
    return this;
  }

  @NotNull
  public PsiReferenceList getThrowsList() {
    return myThrowsList;
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
      builder.append(getAllModifierProperties((LightModifierList) getModifierList()));
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

  public String getAllModifierProperties(LightModifierList modifierList) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      for (String modifier : modifierList.getModifiers()) {
        builder.append(modifier).append(' ');
      }
      return builder.toString();
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public PsiElement copy() {
    return rebuildMethodFromString();
  }

  public String toString() {
    return "LombokLightMethodBuilder: " + getName();
  }

}
