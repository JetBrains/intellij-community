// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.lang.properties.projectView;

import com.intellij.lang.properties.ResourceBundle;
import org.jetbrains.annotations.NotNull;

public interface ResourceBundleAwareNode {
  @NotNull
  ResourceBundle getResourceBundle();
}
