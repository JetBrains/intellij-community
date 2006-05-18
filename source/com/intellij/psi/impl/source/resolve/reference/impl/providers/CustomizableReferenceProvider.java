package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.Map;


/**
 * @author Maxim.Mossienko
 */
public interface CustomizableReferenceProvider extends PsiReferenceProvider {
  final class CustomizationKey<T> {
    private String myOptionDescription;

    CustomizationKey(@NonNls String optionDescription) {
      myOptionDescription = optionDescription;
    }

    public String toString() { return myOptionDescription; }

    public T getValue(@Nullable Map<CustomizationKey,Object> options) {
      return options == null ? null : (T)options.get(this);
    }

    public void putValue(Map<CustomizationKey,Object> options, T value) {
      options.put(this, value);
    }
  }

  void setOptions(@Nullable Map<CustomizationKey,Object> options);
  @Nullable Map<CustomizationKey,Object> getOptions();
}
