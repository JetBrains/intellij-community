// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.navigation;

import com.intellij.psi.PsiElement;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YamlRecursivePsiElementVisitor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;

public class YAMLKeysIndex extends FileBasedIndexExtension<String, Integer> {
  @NonNls
  public static final ID<String, Integer> KEY = ID.create("yaml.keys.name");

  public static final FileBasedIndex.InputFilter YAML_INPUT_FILTER =
    new DefaultFileTypeSpecificInputFilter(YAMLLanguage.INSTANCE.getAssociatedFileType());

  @Override
  public int getVersion() {
    return 1;
  }

  @NotNull
  @Override
  public DataIndexer<String, Integer, FileContent> getIndexer() {
    return new DataIndexer<String, Integer, FileContent>() {
      @NotNull
      @Override
      public Map<String, Integer> map(@NotNull FileContent inputData) {
        final Map<String, Integer> map = new THashMap<>();
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

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public DataExternalizer<Integer> getValueExternalizer() {
    return new DataExternalizer<Integer>() {
      @Override
      public void save(@NotNull DataOutput out, Integer value) throws IOException {
        out.writeInt(value);
      }

      @Override
      public Integer read(@NotNull DataInput in) throws IOException {
        return in.readInt();
      }
    };
  }

  @NotNull
  @Override
  public ID<String, Integer> getName() {
    return KEY;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return YAML_INPUT_FILTER;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }
}
