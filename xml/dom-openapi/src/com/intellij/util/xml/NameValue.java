// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotate a method returning either {@link String} or {@link GenericValue} with @NameValue,
 * and {@link ElementPresentationManager#getElementName(Object)} will return the resulting String
 * or {@link GenericValue#getStringValue()}.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface NameValue {
  /**
   * @return whether the element's name should be unique in some scope ({@link DomFileDescription#getIdentityScope(DomElement)}).
   * If {@code true}, then duplicate values will be highlighted as errors.
   */
  boolean unique() default true;

  /**
   * @return Only usable if the annotated method returns {@code GenericDomValue<Something>}.
   * Then this flag controls whether a reference should be created from the @NameValue child XML element to
   * the XML element of the annotated DOM element. Such a reference is useful in rename refactoring to act as
   * an element's declaration.
   */
  boolean referencable() default true;
}
