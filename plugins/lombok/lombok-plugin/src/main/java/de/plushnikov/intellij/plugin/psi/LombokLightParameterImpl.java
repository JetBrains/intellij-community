package de.plushnikov.intellij.plugin.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.light.LightParameter;
import com.intellij.psi.impl.light.LightVariableBuilder;
import de.plushnikov.intellij.plugin.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public class LombokLightParameterImpl extends LightParameter {
  private final LightIdentifier myNameIdentifier;

  public LombokLightParameterImpl(@NotNull String name, @NotNull PsiType type, PsiElement declarationScope, Language language) {
    super(name, type, declarationScope, language);
    PsiManager manager = declarationScope.getManager();
    myNameIdentifier = new LightIdentifier(manager, name);
    ReflectionUtil.setFinalFieldPerReflection(LightVariableBuilder.class, this, LightModifierList.class,
        new LombokLightModifierListImpl(manager, language));
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier;
  }

  @Override
  public TextRange getTextRange() {
    TextRange r = super.getTextRange();
    return r == null ? TextRange.EMPTY_RANGE : r;
  }

  public LombokLightParameterImpl setModifiers(String... modifiers) {
    LombokLightModifierListImpl modifierList = new LombokLightModifierListImpl(getManager(), getLanguage(), modifiers);
// TODO Check it
//    for (PsiAnnotation annotation : getAnnotations()) {
//      modifierList.addAnnotation(annotation.getQualifiedName());
//    }
    ReflectionUtil.setFinalFieldPerReflection(LightVariableBuilder.class, this, LightModifierList.class, modifierList);
    return this;
  }
}
