package de.plushnikov.intellij.lombok.psi;

import com.intellij.lang.Language;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.Modifier;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Plushnikov Michail
 */
public class LombokLightModifierList10Impl extends LightModifierList {
  private static final Set<String> ALL_MODIFIERS = new HashSet<String>(Arrays.asList(
      PsiModifier.PUBLIC, PsiModifier.PACKAGE_LOCAL, PsiModifier.PROTECTED, PsiModifier.PRIVATE, PsiModifier.FINAL, PsiModifier.STATIC,
      PsiModifier.ABSTRACT, PsiModifier.SYNCHRONIZED, PsiModifier.TRANSIENT, PsiModifier.VOLATILE, PsiModifier.NATIVE));

  private final Map<String, PsiAnnotation> myAnnotations;

  public LombokLightModifierList10Impl(PsiManager manager, final Language language, String... modifiers) {
    super(manager, language, modifiers);
    myAnnotations = CollectionFactory.newTroveMap();
  }

  public void setModifierProperty(@Modifier @NotNull @NonNls String name, boolean value) throws IncorrectOperationException {
    if (value) {
      addModifier(name);
    } else {
      if (hasModifierProperty(name)) {
        removeModifier(name);
      }
    }
  }

  private void removeModifier(@Modifier @NotNull @NonNls String name) {
    final Collection<String> myModifiers = collectAllModifiers();
    myModifiers.remove(name);

    clearModifiers();

    for (String modifier : myModifiers) {
      addModifier(modifier);
    }
  }

  private Collection<String> collectAllModifiers() {
    Collection<String> result = new HashSet<String>();
    for (@Modifier String modifier : ALL_MODIFIERS) {
      if (hasModifierProperty(modifier)) {
        result.add(modifier);
      }
    }
    return result;
  }

  public void checkSetModifierProperty(@Modifier @NotNull @NonNls String name, boolean value) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
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
      result = annotations.toArray(new PsiAnnotation[annotations.size()]);
    }
    return result;
  }

  public String toString() {
    return "LombokLightModifierList10Impl";
  }
}
