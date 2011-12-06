package de.plushnikov.intellij.lombok.psi;

import com.intellij.lang.Language;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public class LombokLightParameter extends LombokLightVariableBuilder implements PsiParameter {
  public static final LombokLightParameter[] EMPTY_ARRAY = new LombokLightParameter[0];
  private final String myName;
  private final PsiElement myDeclarationScope;

  public LombokLightParameter(@NotNull String name, @NotNull PsiType type, @NotNull PsiElement declarationScope, @NotNull Language language) {
    super(declarationScope.getManager(), name, type, language);
    myName = name;
    myDeclarationScope = declarationScope;
  }

  @NotNull
  @Override
  public PsiElement getDeclarationScope() {
    return myDeclarationScope;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitParameter(this);
    }
  }

  public String toString() {
    return "LombokLightParameter: " + getName();
  }

  public boolean isVarArgs() {
    return false;
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @NotNull
  public String getName() {
    return myName;
  }
}
