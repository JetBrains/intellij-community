// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.navigation;

import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
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
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YamlRecursivePsiElementVisitor;

import java.util.Map;

public final class YAMLKeysIndex extends FileBasedIndexExtension<String, Integer> {
  @NonNls
  public static final ID<String, Integer> KEY = ID.create("yaml.keys.name");

  @Override
  public int getVersion() {
    return 1;
  }

  @NotNull
  @Override
  public DataIndexer<String, Integer, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @NotNull
      @Override
      public Map<String, Integer> map(final @NotNull FileContent inputData) {
        final Object2IntMap<String> map = new Object2IntOpenHashMap<>();
        final FileViewProvider provider = inputData.getPsiFile().getViewProvider();
        provider.getPsi(YAMLLanguage.INSTANCE).accept(new YamlRecursivePsiElementVisitor() {
          @Override
          public void visitKeyValue(final @NotNull YAMLKeyValue keyValue) {
            PsiElement key = keyValue.getKey();
            if (key != null) {
              map.put(YAMLUtil.getConfigFullName(keyValue), key.getTextOffset());
            }
            super.visitKeyValue(keyValue);
          }

          @Override
          public void visitSequence(final @NotNull YAMLSequence sequence) {
            // Do not visit children
          }

          @Override
          public void visitOuterLanguageElement(final @NotNull OuterLanguageElement element) {
            // Do not visit outer language elements
          }
        });
        return map;
      }
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public DataExternalizer<Integer> getValueExternalizer() {
    return EnumeratorIntegerDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public ID<String, Integer> getName() {
    return KEY;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return YAMLFileBasedIndexUtil.CONTAINING_YAML_INPUT_FILTER;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }
}
