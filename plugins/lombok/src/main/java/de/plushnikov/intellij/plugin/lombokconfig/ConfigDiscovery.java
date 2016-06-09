package de.plushnikov.intellij.plugin.lombokconfig;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PathUtil;
import com.intellij.util.indexing.FileBasedIndex;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class ConfigDiscovery {
  private final FileBasedIndex fileBasedIndex;

  public static ConfigDiscovery getInstance() {
    return ServiceManager.getService(ConfigDiscovery.class);
  }

  public ConfigDiscovery(FileBasedIndex fileBasedIndex) {
    this.fileBasedIndex = fileBasedIndex;
  }

  @NotNull
  public String getStringLombokConfigProperty(@NotNull ConfigKey configKey, @NotNull PsiClass psiClass) {
    final String canonicalPath = calculateCanonicalPath(psiClass);
    if (null != canonicalPath) {
      return discoverProperty(configKey, canonicalPath, psiClass.getProject());
    } else {
      return configKey.getConfigDefaultValue();
    }
  }

  public boolean getBooleanLombokConfigProperty(@NotNull ConfigKey configKey, @NotNull PsiClass psiClass) {
    final String configProperty = getStringLombokConfigProperty(configKey, psiClass);
    return Boolean.parseBoolean(configProperty);
  }

  @NotNull
  public String[] getMultipleValueLombokConfigProperty(@NotNull ConfigKey configKey, @NotNull PsiClass psiClass) {
    final Collection<String> result = new HashSet<String>();

    final String canonicalPath = calculateCanonicalPath(psiClass);
    if (null != canonicalPath) {
      final List<String> properties = discoverProperties(configKey, canonicalPath, psiClass.getProject());
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
    return result.toArray(new String[result.size()]);
  }

  @Nullable
  private String calculateCanonicalPath(@NotNull PsiClass psiClass) {
    String canonicalPath = null;
    final PsiFile psiFile;
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

    if (null != psiFile) {
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (null != virtualFile) {
        final VirtualFile fileDirectory = virtualFile.getParent();
        if (null != fileDirectory) {
          canonicalPath = fileDirectory.getCanonicalPath();
        }
      }
    }
    return PathUtil.toSystemIndependentName(canonicalPath);
  }

  @NotNull
  private String discoverProperty(@NotNull ConfigKey configKey, @NotNull String canonicalPath, @NotNull Project project) {
    final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);

    String currentPath = canonicalPath;
    while (null != currentPath) {

      final String property = readProperty(fileBasedIndex, searchScope, currentPath, configKey);
      if (null == property) {
        if (shouldStopBubbling(fileBasedIndex, searchScope, currentPath)) {
          break;
        }
      } else {
        return property;
      }

      currentPath = bubbleUp(currentPath);
    }

    return configKey.getConfigDefaultValue();
  }

  @Nullable
  private String bubbleUp(@NotNull String currentPath) {
    final int endIndex = currentPath.lastIndexOf('/');
    if (endIndex > 0) {
      currentPath = currentPath.substring(0, endIndex);
    } else {
      currentPath = null;
    }
    return currentPath;
  }

  private boolean shouldStopBubbling(@NotNull FileBasedIndex fileBasedIndex, @NotNull GlobalSearchScope searchScope, @NotNull String currentPath) {
    final String stopBubblingProperty = readProperty(fileBasedIndex, searchScope, currentPath, ConfigKey.CONFIG_STOP_BUBBLING);
    return Boolean.parseBoolean(stopBubblingProperty);
  }

  @Nullable
  private String readProperty(FileBasedIndex fileBasedIndex, GlobalSearchScope searchScope, String directoryName, ConfigKey configKey) {
    final ConfigIndexKey configIndexKey = new ConfigIndexKey(directoryName, configKey.getConfigKey());
    final List<String> values = fileBasedIndex.getValues(LombokConfigIndex.NAME, configIndexKey, searchScope);
    if (!values.isEmpty()) {
      return values.iterator().next();
    }
    return null;
  }

  @NotNull
  private List<String> discoverProperties(@NotNull ConfigKey configKey, @NotNull String canonicalPath, @NotNull Project project) {
    List<String> result = new ArrayList<String>();

    final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);

    String currentPath = canonicalPath;
    while (null != currentPath) {

      final String property = readProperty(fileBasedIndex, searchScope, currentPath, configKey);
      if (null == property) {
        if (shouldStopBubbling(fileBasedIndex, searchScope, currentPath)) {
          break;
        }
      } else {
        result.add(property);
      }

      currentPath = bubbleUp(currentPath);
    }

    return result;
  }
}
