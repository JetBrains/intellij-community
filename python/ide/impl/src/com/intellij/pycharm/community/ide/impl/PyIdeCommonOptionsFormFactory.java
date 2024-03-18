// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl;

import com.jetbrains.python.run.PyCommonOptionsFormData;
import com.jetbrains.python.run.PyCommonOptionsFormFactory;


public final class PyIdeCommonOptionsFormFactory extends PyCommonOptionsFormFactory {
  @Override
  public PyIdeCommonOptionsForm createForm(PyCommonOptionsFormData data) {
    return new PyIdeCommonOptionsForm(data);
  }
}
