/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.reference.SoftLazyValue;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author cdr
 */
public class PropertiesUtil {
  private static final Pattern LOCALE_PATTERN = Pattern.compile("(_[a-zA-Z]{2,8}(_[a-zA-Z]{2}|[0-9]{3})?(_[\\w\\-]+)?)\\.[^_]+$");
  public static final Set<Character> BASE_NAME_BORDER_CHAR = ContainerUtil.newHashSet('-', '_', '.');
  public static final Locale DEFAULT_LOCALE = new Locale("", "", "");

  private static final SoftLazyValue<Set<String>> LOCALES_LANGUAGE_CODES = new SoftLazyValue<Set<String>>() {
    @NotNull
    @Override
    protected Set<String> compute() {
      final HashSet<String> locales =
        new HashSet<>(ContainerUtil.flatten(ContainerUtil.map(Locale.getAvailableLocales(),
                                                              (Function<Locale, List<String>>)locale -> {
                                                                final ArrayList<String> languages =
                                                                  ContainerUtil.newArrayList(locale.getLanguage());
                                                                try {
                                                                  languages.add(locale.getISO3Language());
                                                                }
                                                                catch (MissingResourceException ignored) {
                                                                  // if ISO3 language is not found for existed locale then exception is thrown anyway
                                                                }
                                                                return languages;
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
  static String getDefaultBaseName(@NotNull final PsiFile file) {
    return CachedValuesManager.getCachedValue(file, new CachedValueProvider<String>() {
      @NotNull
      @Override
      public Result<String> compute() {
        return Result.create(computeBaseName(), file);
      }

      private String computeBaseName() {
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
    });
  }

  @NotNull
  public static Locale getLocale(@NotNull final PropertiesFile propertiesFile) {
    String name = propertiesFile.getName();
    if (!StringUtil.containsChar(name, '_')) return DEFAULT_LOCALE;
    final String containingResourceBundleBaseName = propertiesFile.getResourceBundle().getBaseName();
    if (!name.startsWith(containingResourceBundleBaseName)) return DEFAULT_LOCALE;
    return getLocale(name.substring(containingResourceBundleBaseName.length()));
  }

  @NotNull
  public static Locale getLocale(String suffix) {
    return getLocaleAndTrimmedSuffix(suffix).getFirst();
  }

  @NotNull
  public static Pair<Locale, String> getLocaleAndTrimmedSuffix(String suffix) {
    final Matcher matcher = LOCALE_PATTERN.matcher(suffix);
    if (matcher.find()) {
      final String rawLocale = matcher.group(1);
      final String[] splitRawLocale = rawLocale.split("_");
      if (splitRawLocale.length > 1 && splitRawLocale[1].length() >= 2) {
        final String language = splitRawLocale[1];
        final String country = splitRawLocale.length > 2 ? splitRawLocale[2] : "";
        final String variant = splitRawLocale.length > 3 ? splitRawLocale[3] : "";

        StringBuilder trimmedSuffix = new StringBuilder(language);
        if (!country.isEmpty()) {
          trimmedSuffix.append("_").append(country);
        }
        if (!variant.isEmpty()) {
          trimmedSuffix.append("_").append(variant);
        }

        return Pair.create(new Locale(language, country, variant), trimmedSuffix.toString());
      }
    }
    return Pair.create(DEFAULT_LOCALE, "");
  }

  /**
   * messages_en.properties is a parent of the messages_en_US.properties
   */
  @Nullable
  public static PropertiesFile getParent(@NotNull PropertiesFile file, @NotNull Collection<PropertiesFile> candidates) {
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
    List<IProperty> result = new SmartList<>();
    List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles();
    for (PropertiesFile propertiesFile : propertiesFiles) {
      result.addAll(propertiesFile.findPropertiesByKey(key));
    }
    return result;
  }

  public static List<IProperty> findAllProperties(@NotNull ResourceBundle resourceBundle, String key) {
    List<IProperty> result = new SmartList<>();
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
  static String getPackageQualifiedName(@NotNull PsiDirectory directory) {
    return ProjectRootManager.getInstance(directory.getProject()).getFileIndex().getPackageNameByDirectory(directory.getVirtualFile());
  }

  @NotNull
  public static String getPresentableLocale(@NotNull Locale locale) {
    List<String> names = new ArrayList<>();
    if (!Comparing.strEqual(locale.getDisplayLanguage(), null)) {
      names.add(locale.getDisplayLanguage());
    }
    if (!Comparing.strEqual(locale.getDisplayCountry(), null)) {
      names.add(locale.getDisplayCountry());
    }
    if (!Comparing.strEqual(locale.getDisplayVariant(), null)) {
      names.add(locale.getDisplayVariant());
    }
    return names.isEmpty() ? "" : " (" + StringUtil.join(names, "/") + ")";
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
