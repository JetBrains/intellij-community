// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.highlighting;

import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public final class MockAnnotatingDomInspection<T extends DomElement> extends BasicDomElementsInspection<T>{
  public MockAnnotatingDomInspection(final Class<? extends T> domClass) {
    super(domClass);
  }

  @Override
  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    for (Class aClass : getDomClasses()) {
      helper.runAnnotators(element, holder, aClass);
    }
  }

  @Override
  public @Nls @NotNull String getGroupDisplayName() {
    throw new UnsupportedOperationException("Method getGroupDisplayName is not yet implemented in " + getClass().getName());
  }

  @Override
  public @NonNls @NotNull String getShortName() {
    throw new UnsupportedOperationException("Method getShortName is not yet implemented in " + getClass().getName());
  }

  public static @NotNull Class<? extends DomElementsInspection<?>> getInspection() {
    Class<?> aClass = MockAnnotatingDomInspection.class;
    //noinspection unchecked
    return (Class<? extends DomElementsInspection<?>>)aClass;
  }
}
