package de.plushnikov.intellij.plugin.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Plushnikov Michail
 */
public class LombokLightModifierList extends LightModifierList {
  private static final Set<String> ALL_MODIFIERS = new HashSet<>(Arrays.asList(PsiModifier.MODIFIERS));

  private final Map<String, PsiAnnotation> myAnnotations;
  private final Set<String> myImplicitModifiers;

  public LombokLightModifierList(PsiManager manager, final Language language, Collection<String> implicitModifiers, String... modifiers) {
    super(manager, language, modifiers);
    this.myAnnotations = new HashMap<>();
    this.myImplicitModifiers = new HashSet<>(implicitModifiers);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return myImplicitModifiers.contains(name) || super.hasModifierProperty(name);
  }

  public void addImplicitModifierProperty(@PsiModifier.ModifierConstant @NotNull @NonNls String implicitModifier) {
    myImplicitModifiers.add(implicitModifier);
  }

  public void setModifierProperty(@PsiModifier.ModifierConstant @NotNull @NonNls String name, boolean value) throws IncorrectOperationException {
    if (value) {
      addModifier(name);
    } else {
      if (hasModifierProperty(name)) {
        removeModifier(name);
      }
    }
  }

  private void removeModifier(@PsiModifier.ModifierConstant @NotNull @NonNls String name) {
    final Collection<String> myModifiers = collectAllModifiers();
    myModifiers.remove(name);

    clearModifiers();

    for (String modifier : myModifiers) {
      addModifier(modifier);
    }
  }

  private Collection<String> collectAllModifiers() {
    Collection<String> result = new HashSet<>();
    for (@PsiModifier.ModifierConstant String modifier : ALL_MODIFIERS) {
      if (hasExplicitModifier(modifier)) {
        result.add(modifier);
      }
    }
    return result;
  }

  public void checkSetModifierProperty(@PsiModifier.ModifierConstant @NotNull @NonNls String name, boolean value) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(getProject());
    final PsiAnnotation psiAnnotation = elementFactory.createAnnotationFromText('@' + qualifiedName, null);
    myAnnotations.put(qualifiedName, psiAnnotation);
    return psiAnnotation;
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    return myAnnotations.get(qualifiedName);
  }

  @Override
  @NotNull
  public PsiAnnotation[] getAnnotations() {
    PsiAnnotation[] result = PsiAnnotation.EMPTY_ARRAY;
    if (!myAnnotations.isEmpty()) {
      Collection<PsiAnnotation> annotations = myAnnotations.values();
      result = annotations.toArray(new PsiAnnotation[0]);
    }
    return result;
  }

  @Override
  public TextRange getTextRange() {
    TextRange r = super.getTextRange();
    return r == null ? TextRange.EMPTY_RANGE : r;
  }

  public String toString() {
    return "LombokLightModifierList";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LombokLightModifierList that = (LombokLightModifierList) o;

    return myAnnotations.equals(that.myAnnotations);
  }

  @Override
  public int hashCode() {
    return myAnnotations.hashCode();
  }
}
