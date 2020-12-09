// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.Property;
import org.jetbrains.annotations.NotNull;

public interface DuplicatePropertyKeyAnnotationSuppressor {
  boolean suppressAnnotationFor(@NotNull Property property);
}
