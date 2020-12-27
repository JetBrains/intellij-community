package de.plushnikov.intellij.plugin.lombokconfig;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.util.ArrayUtil;
import com.intellij.util.indexing.FileBasedIndex;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ConfigDiscovery {
  @NotNull
  public static ConfigDiscovery getInstance() {
    return ApplicationManager.getApplication().getService(ConfigDiscovery.class);
  }

  @NotNull
  public String getStringLombokConfigProperty(@NotNull ConfigKey configKey, @NotNull PsiClass psiClass) {
    @Nullable VirtualFile file = calculateDirectory(psiClass);
    if (null != file) {
      return discoverProperty(configKey, file, psiClass.getProject());
    } else {
      return configKey.getConfigDefaultValue();
    }
  }

  public boolean getBooleanLombokConfigProperty(@NotNull ConfigKey configKey, @NotNull PsiClass psiClass) {
    final String configProperty = getStringLombokConfigProperty(configKey, psiClass);
    return Boolean.parseBoolean(configProperty);
  }

  public String @NotNull [] getMultipleValueLombokConfigProperty(@NotNull ConfigKey configKey, @NotNull PsiClass psiClass) {
    final Collection<String> result = new HashSet<>();

    @Nullable VirtualFile file = calculateDirectory(psiClass);
    if (file != null) {
      final List<String> properties = discoverProperties(configKey, file, psiClass.getProject());
      Collections.reverse(properties);

      for (String configProperty : properties) {
        if (StringUtil.isNotEmpty(configProperty)) {
          final String[] values = configProperty.split(";");
          for (String value : values) {
            if (value.startsWith("+")) {
              result.add(value.substring(1));
            } else if (value.startsWith("-")) {
              result.remove(value.substring(1));
            }
          }
        }
      }
    } else {
      result.add(configKey.getConfigDefaultValue());
    }
    return ArrayUtil.toStringArray(result);
  }

  @Nullable
  private static VirtualFile calculateDirectory(@NotNull PsiClass psiClass) {
    PsiFile psiFile;
    if (psiClass instanceof LombokLightClassBuilder) {
      // Use containing class for all LombokLightClasses
      final PsiClass containingClass = psiClass.getContainingClass();
      if (null != containingClass) {
        psiFile = containingClass.getContainingFile();
      } else {
        psiFile = null;
      }
    } else {
      psiFile = psiClass.getContainingFile();
    }
    if (psiFile != null) {
      PsiFile originalFile = psiFile.getOriginalFile();
      if (originalFile != null) {
        psiFile = originalFile;
      }
    }

    return psiFile != null ? psiFile.getVirtualFile() : null;
  }

  @NotNull
  private String discoverProperty(@NotNull ConfigKey configKey, @Nullable VirtualFile file, @NotNull Project project) {
    @Nullable VirtualFile currentFile = file;
    while (currentFile != null) {
      ConfigValue configValue = readProperty(configKey, project, currentFile);
      if (null != configValue) {
        if (null == configValue.getValue()) {
          if (configValue.isStopBubbling()) {
            break;
          }
        } else {
          return configValue.getValue();
        }
      }

      currentFile = currentFile.getParent();
    }

    return configKey.getConfigDefaultValue();
  }

  @VisibleForTesting
  protected FileBasedIndex getFileBasedIndex() {
    return FileBasedIndex.getInstance();
  }

  @Nullable
  private ConfigValue readProperty(@NotNull ConfigKey configKey, @NotNull Project project, @NotNull VirtualFile directory) {
    GlobalSearchScope directoryScope = GlobalSearchScopes.directoryScope(project, directory, false);
    List<ConfigValue> values = getFileBasedIndex().getValues(LombokConfigIndex.NAME, configKey, directoryScope);
    if (!values.isEmpty()) {
      return values.iterator().next();
    }
    return null;
  }

  @NotNull
  private List<String> discoverProperties(@NotNull ConfigKey configKey, @Nullable VirtualFile file, @NotNull Project project) {
    List<String> result = new ArrayList<>();

    @Nullable VirtualFile currentFile = file;
    while (currentFile != null) {
      final ConfigValue configValue = readProperty(configKey, project, currentFile);
      if (null != configValue) {
        if (null == configValue.getValue()) {
          if (configValue.isStopBubbling()) {
            break;
          }
        } else {
          result.add(configValue.getValue());
        }
      }

      currentFile = currentFile.getParent();
    }

    return result;
  }
}
