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

      String packageName = ((PsiJavaFile) psiFile).getPackageName();
      while (null != packageName) {
        final ConfigIndexKey configIndexKey = new ConfigIndexKey(packageName, configKey.getConfigKey());
        final List<String> values = fileBasedIndex.getValues(LombokConfigIndex.NAME, configIndexKey, GlobalSearchScope.projectScope(psiClass.getProject()));
        if (!values.isEmpty()) {
          return values.iterator().next();
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

  public boolean getBooleanLombokConfigProperty(@NotNull ConfigKeys configKey, @NotNull PsiClass psiClass) {
    final String configProperty = getStringLombokConfigProperty(configKey, psiClass);
    return Boolean.parseBoolean(configProperty);
  }

  public boolean getFlagUsageLombokConfigProperty(@NotNull ConfigKeys configKey, @NotNull PsiClass psiClass) {
    final String configProperty = getStringLombokConfigProperty(configKey, psiClass);
    return Boolean.parseBoolean(configProperty);
  }
}
