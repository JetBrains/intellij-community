// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.navigation;

import com.intellij.psi.PsiElement;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLFileBasedIndexUtil;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YamlRecursivePsiElementVisitor;

import java.util.Map;

public final class YAMLKeysIndex extends FileBasedIndexExtension<String, Integer> {
  public static final @NonNls ID<String, Integer> KEY = ID.create("yaml.keys.name");

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public @NotNull DataIndexer<String, Integer, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @Override
      public @NotNull Map<String, Integer> map(@NotNull FileContent inputData) {
        Object2IntMap<String> map = new Object2IntOpenHashMap<>();
        inputData.getPsiFile().accept(new YamlRecursivePsiElementVisitor() {
          @Override
          public void visitKeyValue(@NotNull YAMLKeyValue keyValue) {
            PsiElement key = keyValue.getKey();
            if (key != null) {
              map.put(YAMLUtil.getConfigFullName(keyValue), key.getTextOffset());
            }
            super.visitKeyValue(keyValue);
          }

          @Override
          public void visitSequence(@NotNull YAMLSequence sequence) {
            // Do not visit children
          }
        });
        return map;
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
    return YAMLFileBasedIndexUtil.YAML_INPUT_FILTER;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }
}
