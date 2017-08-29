package de.plushnikov.intellij.plugin.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightPsiClassBuilder;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.icon.LombokIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

public class LombokLightClassBuilder extends LightPsiClassBuilder {
  private final String myQualifiedName;
  private final Icon myBaseIcon;
  private Collection<PsiField> myFields = ContainerUtil.newArrayList();

  public LombokLightClassBuilder(@NotNull PsiElement context, @NotNull String simpleName, @NotNull String qualifiedName) {
    super(context, simpleName);
    myQualifiedName = qualifiedName;
    myBaseIcon = LombokIcons.CLASS_ICON;
  }

  @Override
  @NotNull
  public PsiField[] getFields() {
    return myFields.toArray(new PsiField[myFields.size()]);
  }

  public LightPsiClassBuilder addField(PsiField field) {
    if (field instanceof LightFieldBuilder) {
      ((LightFieldBuilder) field).setContainingClass(this);
    }
    myFields.add(field);
    return this;
  }

  @Override
  public PsiElement getScope() {
    if (getContainingClass() != null) {
      return getContainingClass().getScope();
    }
    return super.getScope();
  }

  @Override
  public PsiElement getParent() {
    return getContainingClass();
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public Icon getElementIcon(final int flags) {
    return myBaseIcon;
  }

  @Override
  public TextRange getTextRange() {
    TextRange r = super.getTextRange();
    return r == null ? TextRange.EMPTY_RANGE : r;
  }

  @Override
  public PsiFile getContainingFile() {
    if (null != getContainingClass()) {
      return getContainingClass().getContainingFile();
    }
    return super.getContainingFile();
  }

  public LombokLightClassBuilder withModifier(@PsiModifier.ModifierConstant @NotNull @NonNls String modifier) {
    getModifierList().addModifier(modifier);
    return this;
  }

  public LombokLightClassBuilder withContainingClass(@NotNull PsiClass containingClass) {
    setContainingClass(containingClass);
    return this;
  }

  public LombokLightClassBuilder withNavigationElement(PsiElement navigationElement) {
    setNavigationElement(navigationElement);
    return this;
  }

  public LombokLightClassBuilder withFields(@NotNull Collection<PsiField> fields) {
    for (PsiField field : fields) {
      addField(field);
    }
    return this;
  }

  public LombokLightClassBuilder withMethods(@NotNull Collection<PsiMethod> methods) {
    for (PsiMethod method : methods) {
      addMethod(method);
    }
    return this;
  }

  public LombokLightClassBuilder withParameterTypes(@Nullable PsiTypeParameterList parameterList) {
    if (parameterList != null) {
      for (PsiTypeParameter typeParameter : parameterList.getTypeParameters()) {
        getTypeParameterList().addParameter(typeParameter);
      }
    }
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LombokLightClassBuilder that = (LombokLightClassBuilder) o;

    return myQualifiedName.equals(that.myQualifiedName);
  }

  @Override
  public int hashCode() {
    return myQualifiedName.hashCode();
  }
}
