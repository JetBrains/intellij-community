package de.plushnikov.intellij.plugin.lombokconfig;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PathUtil;
import com.intellij.util.indexing.FileBasedIndex;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ConfigDiscovery {
  private static ConfigDiscovery ourInstance = new ConfigDiscovery();

  public static ConfigDiscovery getInstance() {
    return ourInstance;
  }

  private ConfigDiscovery() {
  }

  @NotNull
  public String getStringLombokConfigProperty(@NotNull ConfigKeys configKey, @NotNull PsiClass psiClass) {
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
          final String canonicalPath = fileDirectory.getCanonicalPath();
          if (null != canonicalPath) {
            return discoverProperty(configKey, PathUtil.toSystemIndependentName(canonicalPath), psiClass.getProject());
          }
        }
      }
    }
    return configKey.getConfigDefaultValue();
  }

  @NotNull
  private String discoverProperty(@NotNull ConfigKeys configKey, @NotNull String canonicalPath, @NotNull Project project) {
    final FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);

    String currentPath = canonicalPath;
    while (null != currentPath) {

      final String property = readProperty(fileBasedIndex, searchScope, currentPath, configKey);
      if (null == property) {
        final String stopBubblingProperty = readProperty(fileBasedIndex, searchScope, currentPath, ConfigKeys.CONFIG_STOP_BUBBLING);
        if (Boolean.parseBoolean(stopBubblingProperty)) {
          System.out.println("Stop bubbling in: " + currentPath);
          break;
        }
      } else {
        System.out.println("Found property: " + configKey + " in: " + currentPath);
        return property;
      }

      final int endIndex = currentPath.lastIndexOf('/');
      if (endIndex > 0) {
        currentPath = currentPath.substring(0, endIndex);
      } else {
        currentPath = null;
      }
    }

    System.out.println("Return default for property: " + configKey);
    return configKey.getConfigDefaultValue();
  }

  @Nullable
  private String readProperty(FileBasedIndex fileBasedIndex, GlobalSearchScope searchScope, String directoryName, ConfigKeys configKey) {
    final ConfigIndexKey configIndexKey = new ConfigIndexKey(directoryName, configKey.getConfigKey());
    final List<String> values = fileBasedIndex.getValues(LombokConfigIndex.NAME, configIndexKey, searchScope);
    if (!values.isEmpty()) {
      return values.iterator().next();
    }
    return null;
  }

  public boolean getBooleanLombokConfigProperty(@NotNull ConfigKeys configKey, @NotNull PsiClass psiClass) {
    final String configProperty = getStringLombokConfigProperty(configKey, psiClass);
    return Boolean.parseBoolean(configProperty);
  }

  public boolean getFlagUsageLombokConfigProperty(@NotNull ConfigKeys configKey, @NotNull PsiClass psiClass) {
    final String configProperty = getStringLombokConfigProperty(configKey, psiClass);
    return Boolean.parseBoolean(configProperty);
  }
}
