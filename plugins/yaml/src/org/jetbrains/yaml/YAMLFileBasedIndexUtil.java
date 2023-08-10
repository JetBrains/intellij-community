// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml;

import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter;
import com.intellij.util.indexing.FileBasedIndex;

public final class YAMLFileBasedIndexUtil {
  public static final FileBasedIndex.InputFilter YAML_INPUT_FILTER =
    new DefaultFileTypeSpecificInputFilter(YAMLLanguage.INSTANCE.getAssociatedFileType());
}
