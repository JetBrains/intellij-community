package de.plushnikov.intellij.plugin.lombokconfig;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumDataDescriptor;
import com.intellij.util.io.EnumeratorStringDescriptor;
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

public class LombokConfigIndex extends FileBasedIndexExtension<ConfigKey, ConfigValue> {
  @NonNls
  public static final ID<ConfigKey, ConfigValue> NAME = ID.create("LombokConfigIndex");

  @NotNull
  @Override
  public ID<ConfigKey, ConfigValue> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<ConfigKey, ConfigValue, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @NotNull
      @Override
      public Map<ConfigKey, ConfigValue> map(@NotNull FileContent inputData) {
        Map<ConfigKey, ConfigValue> result = Collections.emptyMap();

        final VirtualFile directoryFile = inputData.getFile().getParent();
        if (null != directoryFile) {
          final String canonicalPath = PathUtil.toSystemIndependentName(directoryFile.getCanonicalPath());
          if (null != canonicalPath) {
            final Map<String, String> configValues = extractValues((LombokConfigFile)inputData.getPsiFile());

            final boolean stopBubblingValue = Boolean.parseBoolean(configValues.get(ConfigKey.CONFIG_STOP_BUBBLING.getConfigKey()));
            result = ContainerUtil.map2Map(ConfigKey.values(),
                                           key -> Pair.create(key,
                                                              new ConfigValue(configValues.get(key.getConfigKey()), stopBubblingValue)));
          }
        }
        return result;
      }

      private static Map<String, String> extractValues(LombokConfigFile configFile) {
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
          }
          else {
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
  public KeyDescriptor<ConfigKey> getKeyDescriptor() {
    return new EnumDataDescriptor<>(ConfigKey.class);
  }

  @NotNull
  @Override
  public DataExternalizer<ConfigValue> getValueExternalizer() {
    return new DataExternalizer<>() {
      @Override
      public void save(@NotNull DataOutput out, ConfigValue configValue) throws IOException {
        var isNotNullValue = configValue.getValue() != null;
        out.writeBoolean(isNotNullValue);
        if (isNotNullValue) {
          EnumeratorStringDescriptor.INSTANCE.save(out, configValue.getValue());
        }
        out.writeBoolean(configValue.isStopBubbling());
      }

      @Override
      public ConfigValue read(@NotNull DataInput in) throws IOException {
        var isNotNullValue = in.readBoolean();
        return new ConfigValue(isNotNullValue ? EnumeratorStringDescriptor.INSTANCE.read(in) : null, in.readBoolean());
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
    return 11;
  }
}
