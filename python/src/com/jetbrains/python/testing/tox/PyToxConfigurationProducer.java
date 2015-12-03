/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.testing.tox;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;

/**
 * @author Ilya.Kazakevich
 */
public final class PyToxConfigurationProducer extends RunConfigurationProducer<PyToxConfiguration> {

  public PyToxConfigurationProducer() {
    super(PyToxConfigurationFactory.INSTANCE);
  }

  @Override
  public boolean isConfigurationFromContext(PyToxConfiguration configuration, ConfigurationContext context) {
    return false;
  }

  @Override
  protected boolean setupConfigurationFromContext(PyToxConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    return false;
  }
}
