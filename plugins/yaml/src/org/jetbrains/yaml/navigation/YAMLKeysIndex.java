// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.navigation;

import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLFileBasedIndexUtil;

import java.util.Collections;
import java.util.Map;

public final class YAMLKeysIndex extends FileBasedIndexExtension<String, Integer> {
  public static final @NonNls ID<String, Integer> KEY = ID.create("yaml.keys.name");

  @Override
  public int getVersion() {
    return 2;
  }

  @Override
  public @NotNull DataIndexer<String, Integer, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @Override
      public @NotNull Map<String, Integer> map(@NotNull FileContent inputData) {
        if (inputData instanceof PsiDependentFileContent psiDependentFileContent) {
          return LighterASTTraversalUtils.collectYamlTreeData(psiDependentFileContent.getLighterAST());
        }
        return Collections.emptyMap();
      }
    };
  }

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public @NotNull DataExternalizer<Integer> getValueExternalizer() {
    return EnumeratorIntegerDescriptor.INSTANCE;
  }

  @Override
  public @NotNull ID<String, Integer> getName() {
    return KEY;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return YAMLFileBasedIndexUtil.getYamlInputFilter();
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }
}
