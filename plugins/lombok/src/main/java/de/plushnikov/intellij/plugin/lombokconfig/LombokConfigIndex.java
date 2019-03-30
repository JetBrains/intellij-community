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
import com.intellij.util.io.KeyDescriptor;
import de.plushnikov.intellij.plugin.language.LombokConfigFileType;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigCleaner;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LombokConfigIndex extends FileBasedIndexExtension<ConfigIndexKey, ConfigValue> {
  @NonNls
  public static final ID<ConfigIndexKey, ConfigValue> NAME = ID.create("LombokConfigIndex");

  private static final int INDEX_FORMAT_VERSION = 10;

  @NotNull
  @Override
  public ID<ConfigIndexKey, ConfigValue> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<ConfigIndexKey, ConfigValue, FileContent> getIndexer() {
    return new DataIndexer<ConfigIndexKey, ConfigValue, FileContent>() {
      @NotNull
      @Override
      public Map<ConfigIndexKey, ConfigValue> map(@NotNull FileContent inputData) {
        Map<ConfigIndexKey, ConfigValue> result = Collections.emptyMap();

        final VirtualFile directoryFile = inputData.getFile().getParent();
        if (null != directoryFile) {
          final String canonicalPath = PathUtil.toSystemIndependentName(directoryFile.getCanonicalPath());
          if (null != canonicalPath) {
            final Map<String, String> configValues = extractValues((LombokConfigFile) inputData.getPsiFile());

            final boolean stopBubblingValue = Boolean.parseBoolean(configValues.get(ConfigKey.CONFIG_STOP_BUBBLING.getConfigKey()));
            result = Stream.of(ConfigKey.values())
              .map(ConfigKey::getConfigKey)
              .collect(Collectors.toMap(
                key -> new ConfigIndexKey(canonicalPath, key),
                key -> new ConfigValue(configValues.get(key), stopBubblingValue)));
          }
        }

        return result;
      }

      private Map<String, String> extractValues(LombokConfigFile configFile) {
        Map<String, String> result = new HashMap<>();

        final LombokConfigCleaner[] configCleaners = LombokConfigUtil.getLombokConfigCleaners(configFile);
        for (LombokConfigCleaner configCleaner : configCleaners) {
          final String key = StringUtil.toLowerCase(LombokConfigPsiUtil.getKey(configCleaner));

          final ConfigKey configKey = ConfigKey.fromConfigStringKey(key);
          if (null != configKey) {
            result.put(key, configKey.getConfigDefaultValue());
          }
        }

        final LombokConfigProperty[] configProperties = LombokConfigUtil.getLombokConfigProperties(configFile);
        for (LombokConfigProperty configProperty : configProperties) {
          final String key = StringUtil.toLowerCase(LombokConfigPsiUtil.getKey(configProperty));
          final String value = LombokConfigPsiUtil.getValue(configProperty);
          final String sign = LombokConfigPsiUtil.getSign(configProperty);
          if (null == sign) {
            result.put(key, value);
          } else {
            final String previousValue = StringUtil.defaultIfEmpty(result.get(key), "");
            final String combinedValue = previousValue + sign + value + ";";
            result.put(key, combinedValue);
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
  public DataExternalizer<ConfigValue> getValueExternalizer() {
    return new DataExternalizer<ConfigValue>() {
      @Override
      public void save(@NotNull DataOutput out, ConfigValue configValue) throws IOException {
        final boolean hasNullValue = null == configValue.getValue();
        out.writeBoolean(hasNullValue);
        out.writeUTF(hasNullValue ? "" : configValue.getValue());
        out.writeBoolean(configValue.isStopBubbling());
      }

      @Override
      public ConfigValue read(@NotNull DataInput in) throws IOException {
        final boolean hasNullValue = in.readBoolean();
        final String configValue = in.readUTF();
        final boolean stopBubbling = in.readBoolean();
        return new ConfigValue(hasNullValue ? null : configValue, stopBubbling);
      }
    };
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
