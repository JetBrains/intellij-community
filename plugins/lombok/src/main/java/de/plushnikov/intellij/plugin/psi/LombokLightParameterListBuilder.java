package de.plushnikov.intellij.plugin.psi;

import com.intellij.lang.Language;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SyntheticElement;
import com.intellij.psi.impl.light.LightParameterListBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class LombokLightParameterListBuilder extends LightParameterListBuilder implements SyntheticElement {

  public LombokLightParameterListBuilder(PsiManager manager, Language language) {
    super(manager, language);
  }

  @Override
  public @Nullable LombokLightParameter getParameter(int index) {
    return (LombokLightParameter)super.getParameter(index);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LombokLightParameterListBuilder that = (LombokLightParameterListBuilder)o;

    if (getParametersCount() != that.getParametersCount()) {
      return false;
    }

    return Arrays.equals(getParameters(), that.getParameters());
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(getParameters());
  }
}
