/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyElementVisitor;


public abstract class PyAnnotator extends PyElementVisitor implements PyAnnotatorBase {
  private final boolean myTestMode = ApplicationManager.getApplication().isUnitTestMode();
  private AnnotationHolder _holder;

  @Override
  public boolean isTestMode() {
    return myTestMode;
  }

  @Override
  public AnnotationHolder getHolder() {
    return _holder;
  }

  public void setHolder(AnnotationHolder holder) {
    _holder = holder;
  }

  @Override
  public synchronized void annotateElement(final PsiElement psiElement, final AnnotationHolder holder) {
    setHolder(holder);
    try {
      psiElement.accept(this);
    }
    finally {
      setHolder(null);
    }
  }
}
