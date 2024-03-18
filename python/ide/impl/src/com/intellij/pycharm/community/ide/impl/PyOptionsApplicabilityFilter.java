// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl;

import com.intellij.application.options.OptionId;
import com.intellij.application.options.OptionsApplicabilityFilter;


public final class PyOptionsApplicabilityFilter extends OptionsApplicabilityFilter {
  @Override
  public boolean isOptionApplicable(OptionId optionId) {
    return optionId == OptionId.RENAME_IN_PLACE;
  }
}
