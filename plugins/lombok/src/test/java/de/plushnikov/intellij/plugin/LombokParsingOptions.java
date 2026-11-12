// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * The options of a {@link LombokParsingFixture}. Each setter returns this object.
 */
@TestOnly
public final class LombokParsingOptions {
  private @NotNull LightProjectDescriptor myIncompleteDescriptor = LombokTestUtil.WITHOUT_LOMBOK_JAVA_21_DESCRIPTOR;
  private @NotNull String myAnnotationToComparePattern = ".*";
  private @Nullable Boolean myCompareAnnotations;
  private boolean myCompareCodeBlocks = true;

  /**
   * Sets the descriptor of the module in the {@link LombokParsingFixture.ModeRunnerType#INCOMPLETE} mode.
   * The default is {@link LombokTestUtil#WITHOUT_LOMBOK_JAVA_21_DESCRIPTOR}.
   */
  public @NotNull LombokParsingOptions incompleteDescriptor(@NotNull LightProjectDescriptor descriptor) {
    myIncompleteDescriptor = descriptor;
    return this;
  }

  /**
   * Sets the pattern of the annotations that the fixture compares. The default {@code .*} compares no annotations,
   * unless {@link #compareAnnotations} is set.
   */
  public @NotNull LombokParsingOptions annotationToComparePattern(@NotNull String pattern) {
    myAnnotationToComparePattern = pattern;
    return this;
  }

  /**
   * Sets if the fixture compares the annotations. By default, it compares them when the pattern is not {@code .*}.
   */
  public @NotNull LombokParsingOptions compareAnnotations(boolean compareAnnotations) {
    myCompareAnnotations = compareAnnotations;
    return this;
  }

  /**
   * Sets if the fixture compares the method bodies. The default is {@code true}.
   */
  public @NotNull LombokParsingOptions compareCodeBlocks(boolean compareCodeBlocks) {
    myCompareCodeBlocks = compareCodeBlocks;
    return this;
  }

  @NotNull LightProjectDescriptor getIncompleteDescriptor() {
    return myIncompleteDescriptor;
  }

  @NotNull String getAnnotationToComparePattern() {
    return myAnnotationToComparePattern;
  }

  @Nullable Boolean getCompareAnnotations() {
    return myCompareAnnotations;
  }

  boolean isCompareCodeBlocks() {
    return myCompareCodeBlocks;
  }
}
