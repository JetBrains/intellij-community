// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml;

import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

public final class YAMLFileBasedIndexUtil {
  private static final SynchronizedClearableLazy<FileBasedIndex.InputFilter> YAML_INPUT_FILTER = new SynchronizedClearableLazy<>(() -> {
    return new DefaultFileTypeSpecificInputFilter(YAMLLanguage.INSTANCE.getAssociatedFileType());
  });

  public static @NotNull FileBasedIndex.InputFilter getYamlInputFilter() {
    return YAML_INPUT_FILTER.getValue();
  }
}
