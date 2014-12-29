package de.plushnikov.intellij.plugin.lombokconfig;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

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
    final PsiFile psiFile = psiClass.getContainingFile();
    if (psiFile instanceof PsiJavaFile) {
      final FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
      final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(psiClass.getProject());

      String packageName = ((PsiJavaFile) psiFile).getPackageName();
      while (null != packageName) {

        final String property = readProperty(fileBasedIndex, searchScope, packageName, configKey);
        if (null == property) {
          final String stopBublingProperty = readProperty(fileBasedIndex, searchScope, packageName, ConfigKeys.CONFIG_STOP_BUBBLING);
          if (Boolean.parseBoolean(stopBublingProperty)) {
            break;
          }
        } else {
          return property;
        }

        if (!packageName.isEmpty()) {
          packageName = StringUtil.getPackageName(packageName);
        } else {
          packageName = null;
        }
      }
    }
    return configKey.getConfigDefaultValue();
  }

  private String readProperty(FileBasedIndex fileBasedIndex, GlobalSearchScope searchScope, String packageName, ConfigKeys configKey) {
    final ConfigIndexKey configIndexKey = new ConfigIndexKey(packageName, configKey.getConfigKey());
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
