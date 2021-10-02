// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.highlighting;

import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;

/**
 * Provides basic inspection functionality (resolving, required values, duplicate names, custom annotations).
 */
public abstract class BasicDomElementsInspection<T extends DomElement> extends DomElementsInspection<T> {
  @SafeVarargs
  public BasicDomElementsInspection(@NotNull Class<? extends T> domClass, Class<? extends T>... additionalClasses) {
    super(domClass, additionalClasses);
  }

  /**
   * One may want to create several inspections that check for unresolved DOM references of different types. Through
   * this method one can control these types.
   *
   * @param value GenericDomValue containing references in question
   * @return whether to check for resolve problems
   */
  protected boolean shouldCheckResolveProblems(GenericDomValue<?> value) {
    return true;
  }

  /**
   * The default implementations checks for resolve problems (if {@link #shouldCheckResolveProblems(GenericDomValue)}
   * returns true), then runs annotators (see {@link com.intellij.util.xml.DomFileDescription#createAnnotator()}),
   * checks for {@link com.intellij.util.xml.Required} and {@link com.intellij.util.xml.ExtendClass} annotation
   * problems, checks for name identity (see {@link com.intellij.util.xml.NameValue} annotation) and custom annotation
   * checkers (see {@link DomCustomAnnotationChecker}).
   *
   * @param element element to check
   * @param holder  a place to add problems to
   * @param helper  helper object
   */
  @Override
  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    int oldSize = holder.getSize();
    if (element instanceof GenericDomValue) {
      final GenericDomValue<?> genericDomValue = (GenericDomValue<?>) element;
      if (shouldCheckResolveProblems(genericDomValue)) {
        helper.checkResolveProblems(genericDomValue, holder);
      }
    }
    if (!holder.getFileElement().getFileDescription().isAutomaticHighlightingEnabled()) {
      for (Class<? extends T> aClass : getDomClasses()) {
        helper.runAnnotators(element, holder, aClass);
      }
    }
    if (oldSize != holder.getSize() || !helper.checkRequired(element, holder).isEmpty()) {
      return;
    }

    if (!(element instanceof GenericAttributeValue) && !GenericDomValue.class.equals(ReflectionUtil.getRawType(element.getDomElementType()))) {
      if (!helper.checkNameIdentity(element, holder).isEmpty()) return;
    }

    helper.checkCustomAnnotations(element, holder);
  }

}
