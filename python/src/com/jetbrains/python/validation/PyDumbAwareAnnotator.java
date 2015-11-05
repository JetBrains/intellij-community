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
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyDumbAwareAnnotator implements Annotator, DumbAware {
  public static final ExtensionPointName<PyAnnotator> EP_NAME = ExtensionPointName.create("Pythonid.dumbAnnotator");
  private final PyAnnotator[] myAnnotators;

  public PyDumbAwareAnnotator() {
    myAnnotators = Extensions.getExtensions(EP_NAME);
  }

  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    final PsiFile file = element.getContainingFile();

    for(PyAnnotator annotator: myAnnotators) {
      if (file instanceof PyFileImpl && !((PyFileImpl)file).isAcceptedFor(annotator.getClass())) continue;
      annotator.annotateElement(element, holder);
    }
  }
}
