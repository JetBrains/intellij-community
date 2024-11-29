// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.uiDesigner.compiler;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.CompileContext;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

final class FormBindings {
  private static final Key<Map<File, Collection<File>>> FORMS_TO_COMPILE = Key.create("_forms-to_compile_");

  static void addBinding(@NotNull File srcFile, @NotNull File form, @NotNull Map<File, Collection<File>> container) {
    Collection<File> forms = container.get(srcFile);
    if (forms == null) {
      forms = new ArrayList<>();
      container.put(srcFile, forms);
    }
    forms.add(form);
  }

  static @Nullable Map<File, Collection<File>> getAndClearFormsToCompile(@NotNull CompileContext context) {
    Map<File, Collection<File>> srcToForms = FORMS_TO_COMPILE.get(context);
    FORMS_TO_COMPILE.set(context, null);
    return srcToForms;
  }

  static void setFormsToCompile(@NotNull CompileContext context, @NotNull Map<File, Collection<File>> srcToForms) {
    FORMS_TO_COMPILE.set(context, srcToForms.isEmpty() ? null : srcToForms);
  }
}
