package de.plushnikov.intellij.plugin.resolver;

import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThreeState;
import de.plushnikov.intellij.plugin.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static de.plushnikov.intellij.plugin.LombokClassNames.*;

public class LombokExternalLibraryResolver extends ExternalLibraryResolver {

  private static final List<String> MAIN_LOMBOK_CLASSES =
    Arrays.asList(ALL_ARGS_CONSTRUCTOR, REQUIRED_ARGS_CONSTRUCTOR, NO_ARGS_CONSTRUCTOR,
                  DATA, GETTER, SETTER, EQUALS_AND_HASHCODE, TO_STRING,
                  LOG_4_J, LOG_4_J_2, SLF_4_J, JAVA_LOG, JBOSS_LOG, FLOGGER, COMMONS_LOG, CUSTOM_LOG,
                  VALUE, BUILDER, SUPER_BUILDER, FIELD_DEFAULTS,
                  UTILITY_CLASS, WITH, WITHER, EXPERIMENTAL_DELEGATE,
                  SNEAKY_THROWS, CLEANUP, SYNCHRONIZED);

  private final Set<String> allLombokAnnotations;
  private final Set<String> allLombokPackages;
  private final Map<String, String> simpleNameToPackageMap;

  private static final ExternalLibraryDescriptor LOMBOK = new ExternalLibraryDescriptor("org.projectlombok", "lombok",
                                                                                        null, null, Version.LAST_LOMBOK_VERSION);

  public LombokExternalLibraryResolver() {
    allLombokAnnotations = MAIN_LOMBOK_CLASSES.stream().map(StringUtil::getShortName).collect(Collectors.toUnmodifiableSet());
    allLombokPackages = MAIN_LOMBOK_CLASSES.stream().map(StringUtil::getPackageName).collect(Collectors.toUnmodifiableSet());
    simpleNameToPackageMap = MAIN_LOMBOK_CLASSES.stream().collect(Collectors.toMap(StringUtil::getShortName, Function.identity()));
  }

  @Nullable
  @Override
  public ExternalClassResolveResult resolveClass(@NotNull String shortClassName,
                                                 @NotNull ThreeState isAnnotation,
                                                 @NotNull Module contextModule) {
    if (isAnnotation == ThreeState.YES && allLombokAnnotations.contains(shortClassName)) {
      return new ExternalClassResolveResult(simpleNameToPackageMap.get(shortClassName), LOMBOK);
    }
    return null;
  }

  @Nullable
  @Override
  public ExternalLibraryDescriptor resolvePackage(@NotNull String packageName) {
    if (allLombokPackages.contains(packageName)) {
      return LOMBOK;
    }
    return null;
  }
}
