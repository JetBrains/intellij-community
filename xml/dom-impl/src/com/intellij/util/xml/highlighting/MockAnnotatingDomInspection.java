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
package com.intellij.util.xml.highlighting;

import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class MockAnnotatingDomInspection<T extends DomElement> extends BasicDomElementsInspection<T>{

  public MockAnnotatingDomInspection(final Class<T> domClass) {
    super(domClass);
  }

  @Override
  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    for (final Class aClass : getDomClasses()) {
      helper.runAnnotators(element, holder, aClass);
    }
  }

  @Override
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    throw new UnsupportedOperationException("Method getGroupDisplayName is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    throw new UnsupportedOperationException("Method getDisplayName is not yet implemented in " + getClass().getName());
  }

  @Override
  @NonNls
  @NotNull
  public String getShortName() {
    throw new UnsupportedOperationException("Method getShortName is not yet implemented in " + getClass().getName());
  }
}
