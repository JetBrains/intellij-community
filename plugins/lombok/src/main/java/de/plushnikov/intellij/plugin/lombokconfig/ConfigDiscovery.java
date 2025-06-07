package de.plushnikov.intellij.plugin.lombokconfig;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ConfigDiscovery {
  public static @NotNull ConfigDiscovery getInstance() {
    return ApplicationManager.getApplication().getService(ConfigDiscovery.class);
  }

  public @NotNull LombokNullAnnotationLibrary getAddNullAnnotationLombokConfigProperty(@NotNull PsiClass psiClass) {
    final String configProperty = getStringLombokConfigProperty(ConfigKey.ADD_NULL_ANNOTATIONS, psiClass);
    if (StringUtil.isNotEmpty(configProperty)) {
      for (LombokNullAnnotationLibraryDefned library : LombokNullAnnotationLibraryDefned.values()) {
        if (library.getKey().equalsIgnoreCase(configProperty)) {
          return library;
        }
      }

      final LombokNullAnnotationLibrary parsedCustom = LombokNullAnnotationLibraryCustom.parseCustom(configProperty);
      if (null != parsedCustom) {
        return parsedCustom;
      }
    }
    return LombokNullAnnotationLibraryDefned.NONE;
  }

  public @NotNull Collection<String> getMultipleValueLombokConfigProperty(@NotNull ConfigKey configKey, @NotNull PsiClass psiClass) {
    return getConfigProperty(configKey, psiClass);
  }

  public @NotNull String getStringLombokConfigProperty(@NotNull ConfigKey configKey, @NotNull PsiClass psiClass) {
    Collection<String> result = getConfigProperty(configKey, psiClass);
    if (!result.isEmpty()) {
      return result.iterator().next();
    }
    return configKey.getConfigDefaultValue();
  }

  public boolean getBooleanLombokConfigProperty(@NotNull ConfigKey configKey, @NotNull PsiClass psiClass) {
    final String configProperty = getStringLombokConfigProperty(configKey, psiClass);
    return Boolean.parseBoolean(configProperty);
  }

  private @NotNull Collection<String> getConfigProperty(@NotNull ConfigKey configKey, @NotNull PsiClass psiClass) {
    @Nullable PsiFile psiFile = calculatePsiFile(psiClass);
    if (psiFile != null) {
      return discoverPropertyWithCache(configKey, psiFile);
    }
    return Collections.singletonList(configKey.getConfigDefaultValue());
  }

  private static @Nullable PsiFile calculatePsiFile(@NotNull PsiClass psiClass) {
    PsiFile psiFile = psiClass.getContainingFile();
    if (psiFile != null) {
      psiFile = psiFile.getOriginalFile();
    }
    return psiFile;
  }

  protected @NotNull Collection<String> discoverPropertyWithCache(@NotNull ConfigKey configKey,
                                                                  @NotNull PsiFile psiFile) {
    return CachedValuesManager.getCachedValue(psiFile, () -> {
      Map<ConfigKey, Collection<String>> result =
        ConcurrentFactoryMap.createMap(configKeyInner -> discoverProperty(configKeyInner, psiFile));
      return CachedValueProvider.Result.create(result, LombokConfigChangeListener.CONFIG_CHANGE_TRACKER);
    }).get(configKey);
  }

  protected @NotNull Collection<String> discoverProperty(@NotNull ConfigKey configKey, @NotNull PsiFile psiFile) {
    if (configKey.isConfigScalarValue()) {
      return discoverScalarProperty(configKey, psiFile);
    }
    return discoverCollectionProperty(configKey, psiFile);
  }

  private @NotNull Collection<String> discoverScalarProperty(@NotNull ConfigKey configKey, @NotNull PsiFile psiFile) {
    @Nullable VirtualFile currentFile = psiFile.getVirtualFile();
    while (currentFile != null) {
      ConfigValue configValue = readProperty(configKey, psiFile.getProject(), currentFile);
      if (null != configValue) {
        if (null == configValue.getValue()) {
          if (configValue.isStopBubbling()) {
            break;
          }
        }
        else {
          return Collections.singletonList(configValue.getValue());
        }
      }

      currentFile = currentFile.getParent();
    }

    return Collections.singletonList(configKey.getConfigDefaultValue());
  }

  @VisibleForTesting
  protected FileBasedIndex getFileBasedIndex() {
    return FileBasedIndex.getInstance();
  }

  private @Nullable ConfigValue readProperty(@NotNull ConfigKey configKey, @NotNull Project project, @NotNull VirtualFile directory) {
    if (DumbService.getInstance(project).isAlternativeResolveEnabled()) {
      return LombokConfigIndex.readPropertyWithAlternativeResolver(configKey, project, directory);
    }
    GlobalSearchScope directoryScope = GlobalSearchScopes.directoryScope(project, directory, false);
    List<ConfigValue> values = getFileBasedIndex().getValues(LombokConfigIndex.NAME, configKey, directoryScope);
    if (!values.isEmpty()) {
      return values.iterator().next();
    }
    return null;
  }

  private @NotNull Collection<String> discoverCollectionProperty(@NotNull ConfigKey configKey, @NotNull PsiFile file) {
    List<String> properties = new ArrayList<>();

    final Project project = file.getProject();
    @Nullable VirtualFile currentFile = file.getVirtualFile();
    while (currentFile != null) {
      final ConfigValue configValue = readProperty(configKey, project, currentFile);
      if (null != configValue) {
        if (null == configValue.getValue()) {
          if (configValue.isStopBubbling()) {
            break;
          }
        }
        else {
          properties.add(configValue.getValue());
        }
      }

      currentFile = currentFile.getParent();
    }

    Collections.reverse(properties);

    Collection<String> result = new ArrayList<>();

    for (String configProperty : properties) {
      if (StringUtil.isNotEmpty(configProperty)) {
        final String[] values = configProperty.split(";");
        for (String value : values) {
          if (value.startsWith("+")) {
            final String substring = value.substring(1);
            result.remove(substring);
            result.add(substring);
          }
          else if (value.startsWith("-")) {
            result.remove(value.substring(1));
          }
        }
      }
    }

    return result;
  }
}
