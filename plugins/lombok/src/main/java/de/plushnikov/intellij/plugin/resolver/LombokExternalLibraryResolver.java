package de.plushnikov.intellij.plugin.resolver;

import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ThreeState;
import de.plushnikov.intellij.plugin.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static de.plushnikov.intellij.plugin.LombokClassNames.MAIN_LOMBOK_CLASSES;

public final class LombokExternalLibraryResolver extends ExternalLibraryResolver {

  private final Set<String> allLombokPackages;
  private final Map<String, List<String>> simpleNameToFQNamesMap;

  private static final ExternalLibraryDescriptor LOMBOK_DESCRIPTOR = new ExternalLibraryDescriptor("org.projectlombok", "lombok",
                                                                                                   null, null, Version.LAST_LOMBOK_VERSION,
                                                                                                   DependencyScope.PROVIDED);

  public LombokExternalLibraryResolver() {
    allLombokPackages = MAIN_LOMBOK_CLASSES.stream().map(StringUtil::getPackageName).collect(Collectors.toUnmodifiableSet());
    simpleNameToFQNamesMap = MAIN_LOMBOK_CLASSES.stream().collect(Collectors.groupingBy(StringUtil::getShortName));
  }

  @Override
  public @Nullable ExternalClassResolveResult resolveClass(@NotNull String shortClassName,
                                                           @NotNull ThreeState isAnnotation,
                                                           @NotNull Module contextModule) {
    if (isAnnotation != ThreeState.YES) return null;
    List<String> qualifiedNames = simpleNameToFQNamesMap.get(shortClassName);
    if (qualifiedNames == null) return null;
    return new ExternalClassResolveResult(selectQualifiedName(qualifiedNames, contextModule), LOMBOK_DESCRIPTOR);
  }

  /**
   * Returns the first name that the Lombok of the module has, or the first name when the module has no such class.
   * Lombok 1.18.48 adds {@code lombok.SuperBuilder}, and an older Lombok has only {@code lombok.experimental.SuperBuilder}.
   */
  private static @NotNull String selectQualifiedName(@NotNull List<String> qualifiedNames, @NotNull Module contextModule) {
    if (qualifiedNames.size() == 1) return qualifiedNames.getFirst();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(contextModule.getProject());
    GlobalSearchScope scope = contextModule.getModuleWithDependenciesAndLibrariesScope(true);
    for (String qualifiedName : qualifiedNames) {
      if (facade.findClass(qualifiedName, scope) != null) return qualifiedName;
    }
    return qualifiedNames.getFirst();
  }

  @Override
  public @Nullable ExternalLibraryDescriptor resolvePackage(@NotNull String packageName) {
    if (allLombokPackages.contains(packageName)) {
      return LOMBOK_DESCRIPTOR;
    }
    return null;
  }
}
