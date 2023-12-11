// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.jetbrains.python.run.PyCommonOptionsFormData;
import com.jetbrains.python.run.PyCommonOptionsFormFactory;


public final class PyIdeCommonOptionsFormFactory extends PyCommonOptionsFormFactory {
  @Override
  public PyIdeCommonOptionsForm createForm(PyCommonOptionsFormData data) {
    return new PyIdeCommonOptionsForm(data);
  }
}
