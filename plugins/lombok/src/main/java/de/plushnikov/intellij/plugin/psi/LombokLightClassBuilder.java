package de.plushnikov.intellij.plugin.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightPsiClassBuilder;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import de.plushnikov.intellij.plugin.icon.LombokIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class LombokLightClassBuilder extends LightPsiClassBuilder implements PsiExtensibleClass {
  private boolean myIsEnum;
  private final String myQualifiedName;
  private final Icon myBaseIcon;
  private final LombokLightModifierList myModifierList;
  private Collection<PsiField> myFields = new ArrayList<>();

  public LombokLightClassBuilder(@NotNull PsiElement context, @NotNull String simpleName, @NotNull String qualifiedName) {
    super(context, simpleName);
    myIsEnum = false;
    myQualifiedName = qualifiedName;
    myBaseIcon = LombokIcons.CLASS_ICON;
    myModifierList = new LombokLightModifierList(context.getManager(), context.getLanguage(), Collections.emptyList());
  }

  @NotNull
  @Override
  public LombokLightModifierList getModifierList() {
    return myModifierList;
  }

  @Override
  @NotNull
  public PsiField[] getFields() {
    return myFields.toArray(new PsiField[0]);
  }

  private void addField(PsiField field) {
    if (field instanceof LightFieldBuilder) {
      ((LightFieldBuilder) field).setContainingClass(this);
    }
    myFields.add(field);
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

  @Override
  public boolean isEnum() {
    return myIsEnum;
  }

  public LombokLightClassBuilder withEnum(boolean isEnum) {
    myIsEnum = isEnum;
    return this;
  }

  public LombokLightClassBuilder withImplicitModifier(@PsiModifier.ModifierConstant @NotNull @NonNls String modifier) {
    myModifierList.addImplicitModifierProperty(modifier);
    return this;
  }

  public LombokLightClassBuilder withModifier(@PsiModifier.ModifierConstant @NotNull @NonNls String modifier) {
    myModifierList.addModifier(modifier);
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

  public LombokLightClassBuilder withExtends(PsiClass baseClass) {
    getExtendsList().addReference(baseClass);
    return this;
  }

  public LombokLightClassBuilder withExtends(PsiClassType baseClassType) {
    getExtendsList().addReference(baseClassType);
    return this;
  }

  public LombokLightClassBuilder withField(@NotNull PsiField psiField) {
    addField(psiField);
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
      Stream.of(parameterList.getTypeParameters()).forEach(this::withParameterType);
    }
    return this;
  }

  public LombokLightClassBuilder withParameterType(@NotNull PsiTypeParameter psiTypeParameter) {
    getTypeParameterList().addParameter(psiTypeParameter);
    return this;
  }

  @NotNull
  @Override
  public List<PsiField> getOwnFields() {
    return Arrays.asList(getFields());
  }

  @NotNull
  @Override
  public List<PsiMethod> getOwnMethods() {
    return Arrays.asList(getMethods());
  }

  @NotNull
  @Override
  public List<PsiClass> getOwnInnerClasses() {
    return Arrays.asList(getInnerClasses());
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
