package de.plushnikov.intellij.plugin.lombokconfig;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class LombokConfigIndex extends FileBasedIndexExtension<ConfigKey, ConfigValue> {
  private static final @NotNull String LOMBOK_CONFIG_FILE_NAME = "lombok.config";
  public static final @NonNls ID<ConfigKey, ConfigValue> NAME = ID.create("LombokConfigIndex");

  @Override
  public @NotNull ID<ConfigKey, ConfigValue> getName() {
    return NAME;
  }

  @Override
  public @NotNull DataIndexer<ConfigKey, ConfigValue, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @Override
      public @NotNull Map<ConfigKey, ConfigValue> map(@NotNull FileContent inputData) {
        final VirtualFile directoryFile = inputData.getFile().getParent();
        if (null != directoryFile) {
          final String canonicalPath = PathUtil.toSystemIndependentName(directoryFile.getCanonicalPath());
          if (null != canonicalPath) {
            PsiFile psiFile = inputData.getPsiFile();
            return createConfigMapResult(psiFile);
          }
        }
        return Collections.emptyMap();
      }
    };
  }

  private static @Unmodifiable @NotNull Map<ConfigKey, ConfigValue> createConfigMapResult(@Nullable PsiFile psiFile) {
    if (!(psiFile instanceof LombokConfigFile)) {
      return Map.of();
    }
    final Map<String, String> configValues = extractValues((LombokConfigFile)psiFile);

    final boolean stopBubblingValue =
      Boolean.parseBoolean(configValues.get(StringUtil.toLowerCase(ConfigKey.CONFIG_STOP_BUBBLING.getConfigKey())));
    return ContainerUtil.map2Map(ConfigKey.values(),
                                   key -> Pair.create(key,
                                                      new ConfigValue(configValues.get(StringUtil.toLowerCase(key.getConfigKey())),
                                                                      stopBubblingValue)));
  }

  private static Map<String, String> extractValues(LombokConfigFile configFile) {
    Map<String, String> result = new HashMap<>();

    final LombokConfigCleaner[] configCleaners = LombokConfigUtil.getLombokConfigCleaners(configFile);
    for (LombokConfigCleaner configCleaner : configCleaners) {
      final String key = StringUtil.toLowerCase(configCleaner.getKey());

      final ConfigKey configKey = ConfigKey.fromConfigStringKey(key);
      if (null != configKey) {
        result.put(key, configKey.getConfigDefaultValue());
      }
    }

    final LombokConfigProperty[] configProperties = LombokConfigUtil.getLombokConfigProperties(configFile);
    for (LombokConfigProperty configProperty : configProperties) {
      final String key = StringUtil.toLowerCase(configProperty.getKey());
      final String value = StringUtil.notNullize(configProperty.getValue());
      final String sign = configProperty.getSign();
      if (null == sign) {
        result.put(key, value);
      }
      else {
        final String previousValue = result.getOrDefault(key, "");
        final String combinedValue = previousValue + sign + value + ";";
        result.put(key, combinedValue);
      }
    }

    return result;
  }

  @Override
  public @NotNull KeyDescriptor<ConfigKey> getKeyDescriptor() {
    return new EnumDataDescriptor<>(ConfigKey.class);
  }

  @Override
  public @NotNull DataExternalizer<ConfigValue> getValueExternalizer() {
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

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(LombokConfigFileType.INSTANCE);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 15;
  }

  static @Nullable ConfigValue readPropertyWithAlternativeResolver(@NotNull ConfigKey key,
                                                                   @NotNull Project project,
                                                                   @NotNull VirtualFile directory) {
    VirtualFile configVirtualFile = directory.findFileByRelativePath(LOMBOK_CONFIG_FILE_NAME);
    if (configVirtualFile == null) {
      return null;
    }
    Document document = FileDocumentManager.getInstance().getDocument(configVirtualFile);
    if (document == null) {
      return null;
    }
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile == null) {
      return null;
    }
    Map<ConfigKey, ConfigValue> values = CachedValuesManager.getCachedValue(psiFile, () -> {
      Map<ConfigKey, ConfigValue> result = createConfigMapResult(psiFile);
      //CONFIG_CHANGE_TRACKER is not really accurate, but it is already used for this purpose
      return CachedValueProvider.Result.create(result, LombokConfigChangeListener.CONFIG_CHANGE_TRACKER);
    });
    return values.get(key);
  }
}
