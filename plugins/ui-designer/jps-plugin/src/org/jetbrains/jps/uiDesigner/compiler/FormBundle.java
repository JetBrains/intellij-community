// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.uiDesigner.compiler;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.jps.api.JpsDynamicBundle;

/**
 * @author Alexander Lobas
 */
public class FormBundle extends JpsDynamicBundle {
  private static final String BUNDLE = "messages.FormBundle";
  private static final FormBundle INSTANCE = new FormBundle();

  private FormBundle() {
    super(BUNDLE);
  }

  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }
}