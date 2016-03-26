package de.plushnikov.intellij.plugin.lombokconfig;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import de.plushnikov.intellij.plugin.language.LombokConfigFileType;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigFile;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigProperty;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigPsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LombokConfigIndex extends FileBasedIndexExtension<ConfigIndexKey, String> {
  @NonNls
  public static final ID<ConfigIndexKey, String> NAME = ID.create("LombokConfigIndex");
  private static final int INDEX_FORMAT_VERSION = 4;

  @NotNull
  @Override
  public ID<ConfigIndexKey, String> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<ConfigIndexKey, String, FileContent> getIndexer() {
    return new DataIndexer<ConfigIndexKey, String, FileContent>() {
      @NotNull
      @Override
      public Map<ConfigIndexKey, String> map(@NotNull FileContent inputData) {
        Map<ConfigIndexKey, String> result = Collections.emptyMap();

        final VirtualFile directoryFile = inputData.getFile().getParent();
        if (null != directoryFile) {
          final String canonicalPath = PathUtil.toSystemIndependentName(directoryFile.getCanonicalPath());
          if (null != canonicalPath) {
            final LombokConfigProperty[] configProperties = LombokConfigUtil.getLombokConfigProperties((LombokConfigFile) inputData.getPsiFile());
            System.out.println("Index lombok.config in: " + canonicalPath);
            result = new HashMap<ConfigIndexKey, String>();
            for (LombokConfigProperty configProperty : configProperties) {
              result.put(new ConfigIndexKey(canonicalPath, LombokConfigPsiUtil.getKey(configProperty)),
                  LombokConfigPsiUtil.getValue(configProperty));
            }
          }
        }

        return result;
      }
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<ConfigIndexKey> getKeyDescriptor() {
    return new KeyDescriptor<ConfigIndexKey>() {
      @Override
      public int getHashCode(ConfigIndexKey configKey) {
        return configKey.hashCode();
      }

      @Override
      public boolean isEqual(ConfigIndexKey val1, ConfigIndexKey val2) {
        return val1.equals(val2);
      }

      @Override
      public void save(@NotNull DataOutput out, ConfigIndexKey value) throws IOException {
        out.writeUTF(StringUtil.notNullize(value.getDirectoryName()));
        out.writeUTF(StringUtil.notNullize(value.getConfigKey()));
      }

      @Override
      public ConfigIndexKey read(@NotNull DataInput in) throws IOException {
        return new ConfigIndexKey(in.readUTF(), in.readUTF());
      }
    };
  }

  @NotNull
  @Override
  public DataExternalizer<String> getValueExternalizer() {
    return new EnumeratorStringDescriptor();
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(LombokConfigFileType.INSTANCE);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return INDEX_FORMAT_VERSION;
  }
}
