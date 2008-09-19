package com.intellij.application.options;

/**
 * @author yole
 */
public class XmlOptionsApplicabilityFilter extends OptionsApplicabilityFilter {
  public boolean isOptionApplicable(final OptionId optionId) {
    return optionId == OptionId.COMPLETION_AUTO_POPUP_XML;
  }
}
