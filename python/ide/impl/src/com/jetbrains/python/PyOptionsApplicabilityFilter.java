// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.application.options.OptionId;
import com.intellij.application.options.OptionsApplicabilityFilter;


public final class PyOptionsApplicabilityFilter extends OptionsApplicabilityFilter {
  @Override
  public boolean isOptionApplicable(OptionId optionId) {
    return optionId == OptionId.RENAME_IN_PLACE;
  }
}
