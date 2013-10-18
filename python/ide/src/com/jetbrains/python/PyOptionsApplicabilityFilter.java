package com.jetbrains.python;

import com.intellij.application.options.OptionId;
import com.intellij.application.options.OptionsApplicabilityFilter;

/**
 * @author yole
 */
public class PyOptionsApplicabilityFilter extends OptionsApplicabilityFilter {
  @Override
  public boolean isOptionApplicable(OptionId optionId) {
    return optionId == OptionId.RENAME_IN_PLACE;
  }
}
