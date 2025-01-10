// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.uiDesigner.compiler;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.CompileContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

final class FormBindings {
  private static final Key<Map<Path, Collection<Path>>> FORMS_TO_COMPILE = Key.create("_forms-to_compile_");

  static void addBinding(@NotNull Path srcFile, @NotNull Path form, @NotNull Map<Path, Collection<Path>> container) {
    container.computeIfAbsent(srcFile, k -> new ArrayList<>()).add(form);
  }

  static @Nullable Map<Path, Collection<Path>> getAndClearFormsToCompile(@NotNull CompileContext context) {
    Map<Path, Collection<Path>> srcToForms = FORMS_TO_COMPILE.get(context);
    FORMS_TO_COMPILE.set(context, null);
    return srcToForms;
  }

  static void setFormsToCompile(@NotNull CompileContext context, @NotNull Map<Path, Collection<Path>> srcToForms) {
    FORMS_TO_COMPILE.set(context, srcToForms.isEmpty() ? null : srcToForms);
  }
}
