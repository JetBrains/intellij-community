/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.xml.highlighting;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Basic DOM inspection (see {@link BasicDomElementsInspection})
 * calls this annotator on all DOM elements with the given custom user-defined annotation.
 */
public abstract class DomCustomAnnotationChecker<T extends Annotation> {
  static final ExtensionPointName<DomCustomAnnotationChecker> EP_NAME = ExtensionPointName.create("com.intellij.dom.customAnnotationChecker");
  
  @NotNull
  public abstract Class<T> getAnnotationClass();

  public abstract List<DomElementProblemDescriptor> checkForProblems(@NotNull T t, @NotNull DomElement element, @NotNull DomElementAnnotationHolder holder, @NotNull DomHighlightingHelper helper);
}
