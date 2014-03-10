package de.plushnikov.intellij.plugin.provider;

import com.intellij.psi.PsiElement;

public class AugmentCallData {
  private final PsiElement element;
  private final Class type;

  public <Psi extends PsiElement> AugmentCallData(PsiElement element, Class<Psi> type) {
    this.element = element;
    this.type = type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    AugmentCallData that = (AugmentCallData) o;

    return element.equals(that.element) && type.equals(that.type);
  }

  @Override
  public int hashCode() {
    int result = element.hashCode();
    result = 31 * result + type.hashCode();
    return result;
  }
}
