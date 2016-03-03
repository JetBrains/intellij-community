/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.reference.SoftLazyValue;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.*;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.ui.plaf.beg.BegResources.j;

/**
 * @author cdr
 */
public class PropertiesUtil {
  public final static Pattern LOCALE_PATTERN = Pattern.compile("(_[a-zA-Z]{2,8}(_[a-zA-Z]{2}|[0-9]{3})?(_[\\w\\-]+)?)\\.[^_]+$");
  public final static Set<Character> BASE_NAME_BORDER_CHAR = ContainerUtil.newHashSet('-', '_', '.');
  public final static Locale DEFAULT_LOCALE = new Locale("", "", "");

  private static final SoftLazyValue<Set<String>> LOCALES_LANGUAGE_CODES = new SoftLazyValue<Set<String>>() {
    @NotNull
    @Override
    protected Set<String> compute() {
      final HashSet<String> locales =
        new HashSet<String>(ContainerUtil.flatten(ContainerUtil.map(Locale.getAvailableLocales(), new Function<Locale, List<String>>() {
          @Override
          public List<String> fun(Locale locale) {
            final ArrayList<String> languages = ContainerUtil.newArrayList(locale.getLanguage());
            try {
              languages.add(locale.getISO3Language());
            }
            catch (MissingResourceException ignored) {
              // if ISO3 language is not found for existed locale then exception is thrown anyway
            }
            return languages;
          }
        })));
      locales.addAll(ContainerUtil.newArrayList(Locale.getISOLanguages()));
      return locales;
    }
  };


  public static boolean containsProperty(final ResourceBundle resourceBundle, final String propertyName) {
    for (PropertiesFile propertiesFile : resourceBundle.getPropertiesFiles()) {
      if (propertiesFile.findPropertyByKey(propertyName) != null) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static String getDefaultBaseName(final Collection<PropertiesFile> files) {
    String commonPrefix = null;
    for (PropertiesFile file : files) {
      final String baseName = file.getVirtualFile().getNameWithoutExtension();
      if (commonPrefix == null) {
        commonPrefix = baseName;
      } else {
        commonPrefix = StringUtil.commonPrefix(commonPrefix, baseName);
        if (commonPrefix.isEmpty()) {
          break;
        }
      }
    }
    assert commonPrefix != null;
    if (!commonPrefix.isEmpty() && BASE_NAME_BORDER_CHAR.contains(commonPrefix.charAt(commonPrefix.length() - 1))) {
      commonPrefix = commonPrefix.substring(0, commonPrefix.length() - 1);
    }
    return commonPrefix;
  }

  @NotNull
  public static String getDefaultBaseName(@NotNull final VirtualFile file) {
    final String name = file.getName();

    if (!StringUtil.containsChar(name, '_')) {
      return FileUtil.getNameWithoutExtension(name);
    }

    final Matcher matcher = LOCALE_PATTERN.matcher(name);
    final String baseNameWithExtension;

    int matchIndex = 0;
    while (matcher.find(matchIndex)) {
      final MatchResult matchResult = matcher.toMatchResult();
      final String[] splitted = matchResult.group(1).split("_");
      if (splitted.length > 1) {
        final String langCode = splitted[1];
        if (!LOCALES_LANGUAGE_CODES.getValue().contains(langCode)) {
          matchIndex = matchResult.start(1) + 1;
          continue;
        }
        baseNameWithExtension = name.substring(0, matchResult.start(1)) + name.substring(matchResult.end(1));
        return FileUtil.getNameWithoutExtension(baseNameWithExtension);
      }
    }
    baseNameWithExtension = name;
    return FileUtil.getNameWithoutExtension(baseNameWithExtension);
  }

  @NotNull
  public static Locale getLocale(final @NotNull PropertiesFile propertiesFile) {
    String name = propertiesFile.getName();
    if (!StringUtil.containsChar(name, '_')) return DEFAULT_LOCALE;
    final String containingResourceBundleBaseName = propertiesFile.getResourceBundle().getBaseName();
    if (!name.startsWith(containingResourceBundleBaseName)) return DEFAULT_LOCALE;
    return getLocale(name.substring(containingResourceBundleBaseName.length()));
  }

  public static Locale getLocale(String suffix) {
    final Matcher matcher = LOCALE_PATTERN.matcher(suffix);
    if (matcher.find()) {
      final String rawLocale = matcher.group(1);
      final String[] splittedRawLocale = rawLocale.split("_");
      if (splittedRawLocale.length > 1 && splittedRawLocale[1].length() >= 2) {
        final String language = splittedRawLocale[1];
        final String country = splittedRawLocale.length > 2 ? splittedRawLocale[2] : "";
        final String variant = splittedRawLocale.length > 3 ? splittedRawLocale[3] : "";
        return new Locale(language, country, variant);
      }
    }
    return DEFAULT_LOCALE;
  }

  /**
   * messages_en.properties is a parent of the messages_en_US.properties
   */
  @Nullable
  public static PropertiesFile getParent(PropertiesFile file, List<PropertiesFile> candidates) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;
    String name = virtualFile.getNameWithoutExtension();
    String[] parts = name.split("_");
    if (parts.length == 1) return null;
    List<String> partsList = Arrays.asList(parts);
    for (int i=parts.length-1; i>=1;i--) {
      String parentName = StringUtil.join(partsList.subList(0, i), "_") + "." + virtualFile.getExtension();
      for (PropertiesFile candidate : candidates) {
        if (parentName.equals(candidate.getName())) return candidate;
      }
    }
    return null;
  }

  /**
   * @deprecated use PropertiesUtil.findAllProperties(ResourceBundle resourceBundle, String key)
   */
  @NotNull
  @Deprecated
  public static List<IProperty> findAllProperties(Project project, @NotNull ResourceBundle resourceBundle, String key) {
    List<IProperty> result = new SmartList<IProperty>();
    List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles();
    for (PropertiesFile propertiesFile : propertiesFiles) {
      result.addAll(propertiesFile.findPropertiesByKey(key));
    }
    return result;
  }

  public static List<IProperty> findAllProperties(@NotNull ResourceBundle resourceBundle, String key) {
    List<IProperty> result = new SmartList<IProperty>();
    List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles();
    for (PropertiesFile propertiesFile : propertiesFiles) {
      result.addAll(propertiesFile.findPropertiesByKey(key));
    }
    return result;
  }

  public static boolean isUnescapedBackSlashAtTheEnd (String text) {
    boolean result = false;
    for (int i = text.length()-1; i>=0; i--) {
      if (text.charAt(i) == '\\') {
        result = !result;
      }
      else {
        break;
      }
    }
    return result;
  }

  @Nullable
  public static String getPackageQualifiedName(@NotNull PsiDirectory directory) {
    return ProjectRootManager.getInstance(directory.getProject()).getFileIndex().getPackageNameByDirectory(directory.getVirtualFile());
  }

  @NotNull
  public static String getPresentableLocale(@NotNull Locale locale) {
    List<String> names = new ArrayList<String>();
    if (!Comparing.strEqual(locale.getDisplayLanguage(), null)) {
      names.add(locale.getDisplayLanguage());
    }
    if (!Comparing.strEqual(locale.getDisplayCountry(), null)) {
      names.add(locale.getDisplayCountry());
    }
    if (!Comparing.strEqual(locale.getDisplayVariant(), null)) {
      names.add(locale.getDisplayVariant());
    }
    return names.isEmpty() ? "" : (" (" + StringUtil.join(names, "/") + ")");
  }

  public static boolean hasDefaultLanguage(Locale locale) {
    return LOCALES_LANGUAGE_CODES.getValue().contains(locale.getLanguage());
  }

  @NotNull
  public static String getSuffix(@NotNull PropertiesFile propertiesFile) {
    final String baseName = propertiesFile.getResourceBundle().getBaseName();
    final String propertiesFileName = propertiesFile.getName();
    if (baseName.equals(FileUtil.getNameWithoutExtension(propertiesFileName))) return "";
    return FileUtil.getNameWithoutExtension(propertiesFileName.substring(baseName.length() + 1));
  }
}
