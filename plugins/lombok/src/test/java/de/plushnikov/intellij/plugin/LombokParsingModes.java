// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin;

import de.plushnikov.intellij.plugin.LombokParsingFixture.ModeRunnerType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.platform.commons.support.AnnotationSupport;

import java.util.stream.Stream;

/**
 * Gives one invocation of a {@link LombokParsingTest} for each mode, and stores the mode for {@link LombokParsingFixture}.
 */
@TestOnly
final class LombokParsingModes implements TestTemplateInvocationContextProvider {
  private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(LombokParsingModes.class);

  @Override
  public boolean supportsTestTemplate(@NotNull ExtensionContext context) {
    return AnnotationSupport.isAnnotated(context.getTestMethod(), LombokParsingTest.class);
  }

  @Override
  public @NotNull Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(@NotNull ExtensionContext context) {
    LombokParsingTest annotation = AnnotationSupport.findAnnotation(context.getRequiredTestMethod(), LombokParsingTest.class).orElseThrow();
    return LombokParsingFixture.modes().stream()
      .filter(mode -> annotation.incompleteMode() || mode != ModeRunnerType.INCOMPLETE)
      .map(mode -> new ModeInvocation(mode));
  }

  /**
   * Returns the mode of the invocation of {@code context}, or {@link ModeRunnerType#NORMAL} for a test that is not a {@link LombokParsingTest}.
   */
  static @NotNull ModeRunnerType getMode(@NotNull ExtensionContext context) {
    ModeRunnerType mode = context.getStore(NAMESPACE).get(ModeRunnerType.class, ModeRunnerType.class);
    return mode != null ? mode : ModeRunnerType.NORMAL;
  }

  private record ModeInvocation(@NotNull ModeRunnerType mode) implements TestTemplateInvocationContext {
    @Override
    public @NotNull String getDisplayName(int invocationIndex) {
      return mode.name();
    }

    @Override
    public void prepareInvocation(@NotNull ExtensionContext context) {
      context.getStore(NAMESPACE).put(ModeRunnerType.class, mode);
    }
  }
}
