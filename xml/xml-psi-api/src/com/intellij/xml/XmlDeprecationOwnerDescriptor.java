// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.meta.PsiMetaData;
import org.jetbrains.annotations.ApiStatus;

/**
 * Interface-marker that provides a way to customize "deprecated" behaviour for {@link XmlAttributeDescriptor} and {@link  XmlElementDescriptor}.
 * The default platform implementation searches for "deprecated" text around the tag declaration {@link PsiMetaData#getDeclaration()}
 */
@ApiStatus.Experimental
public interface XmlDeprecationOwnerDescriptor {

  /**
   * @return if the symbol (tag name or attribute name) should be highlighted as {@link ProblemHighlightType#LIKE_DEPRECATED}
   */
  boolean isDeprecated();
}