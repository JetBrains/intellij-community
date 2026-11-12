// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin;

import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs a test of a {@link LombokParsingFixture} once in each mode of {@link LombokParsingFixture#modes()}.
 * The display name of an invocation is the mode. The fixture reads the mode of the invocation, so the test method takes no parameter.
 */
@TestOnly
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(LombokParsingModes.class)
public @interface LombokParsingTest {
  /**
   * Set it to {@code false} to leave out the {@link LombokParsingFixture.ModeRunnerType#INCOMPLETE} mode,
   * for a processor that does not support it.
   */
  boolean incompleteMode() default true;
}
