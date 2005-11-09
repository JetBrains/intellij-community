package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;


/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Nov 9, 2005
 * Time: 7:25:54 PM
 * To change this template use File | Settings | File Templates.
 */
public interface CustomizableReferenceProvider extends PsiReferenceProvider {
  final class CustomizationKey<Option> {
    private String myOptionDescription;

    CustomizationKey(String optionDescription) {
      myOptionDescription = optionDescription;
    }

    public String toString() { return myOptionDescription; }

    public Option getValue(Map<CustomizationKey,Object> options) {
      return (Option)options.get(this);
    }

    public void putValue(Map<CustomizationKey,Object> options, Option value) {
      options.put(this, value);
    }
  }

  void setOptions(@Nullable Map<CustomizationKey,Object> options);
}
