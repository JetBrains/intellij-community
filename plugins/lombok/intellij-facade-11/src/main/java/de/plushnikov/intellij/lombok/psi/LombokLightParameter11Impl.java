package de.plushnikov.intellij.lombok.psi;

import com.intellij.lang.Language;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.light.LightParameter;
import com.intellij.psi.impl.light.LightVariableBuilder;
import de.plushnikov.intellij.lombok.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public class LombokLightParameter11Impl extends LightParameter {
  private final LightIdentifier myNameIdentifier;

  public LombokLightParameter11Impl(@NotNull String name, @NotNull PsiType type, PsiElement declarationScope, Language language) {
    super(name, type, declarationScope, language);
    PsiManager manager = declarationScope.getManager();
    myNameIdentifier = new LightIdentifier(manager, name);
    ReflectionUtil.setFinalFieldPerReflection(LightVariableBuilder.class, this, LightModifierList.class,
        new LombokLightModifierList11Impl(manager, language));
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier;
  }

  public LombokLightParameter11Impl setModifiers(String... modifiers) {
    LombokLightModifierList11Impl modifierList = new LombokLightModifierList11Impl(getManager(), getLanguage(), modifiers);
    for (PsiAnnotation annotation : getAnnotations()) {
      modifierList.addAnnotation(annotation.getQualifiedName());
    }
    ReflectionUtil.setFinalFieldPerReflection(LightVariableBuilder.class, this, LightModifierList.class, modifierList);
    return this;
  }
}
