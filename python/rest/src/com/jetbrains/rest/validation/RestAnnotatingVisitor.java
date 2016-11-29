/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.rest.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.jetbrains.rest.RestFileType;
import com.jetbrains.rest.RestLanguage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User : catherine
 */
public class RestAnnotatingVisitor implements Annotator {
  private static final Logger LOGGER = Logger.getInstance(RestAnnotatingVisitor.class.getName());
  private final List<RestAnnotator> myAnnotators = new ArrayList<>();

  public RestAnnotatingVisitor() {
    for (Class<? extends RestAnnotator> cls : ((RestLanguage)RestFileType.INSTANCE.getLanguage()).getAnnotators()) {
      RestAnnotator annotator;
      try {
        annotator = cls.newInstance();
      }
      catch (InstantiationException e) {
        LOGGER.error(e);
        continue;
      }
      catch (IllegalAccessException e) {
        LOGGER.error(e);
        continue;
      }
      myAnnotators.add(annotator);
    }
  }

  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    for(RestAnnotator annotator: myAnnotators) {
      annotator.annotateElement(psiElement, holder);
    }
  }
}
