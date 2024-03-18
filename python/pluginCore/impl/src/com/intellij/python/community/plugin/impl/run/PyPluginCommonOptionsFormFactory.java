// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.impl.run;


import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.PyCommonOptionsFormData;
import com.jetbrains.python.run.PyCommonOptionsFormFactory;

public final class PyPluginCommonOptionsFormFactory extends PyCommonOptionsFormFactory {
  @Override
  public AbstractPyCommonOptionsForm createForm(PyCommonOptionsFormData data) {
    return new PyPluginCommonOptionsForm(data);
  }
}
