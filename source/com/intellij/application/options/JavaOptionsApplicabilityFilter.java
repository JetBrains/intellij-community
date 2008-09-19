package com.intellij.application.options;

/**
 * @author yole
 */
public class JavaOptionsApplicabilityFilter extends OptionsApplicabilityFilter {
  public boolean isOptionApplicable(final OptionId optionId) {
    // all options are applicable for Java
    return true;
  }
}
