// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.resolve;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLAlias;
import org.jetbrains.yaml.psi.YAMLAnchor;
import org.jetbrains.yaml.psi.YamlRecursivePsiElementVisitor;

import java.util.*;

public final class YAMLLocalResolveUtil {
  private YAMLLocalResolveUtil() {}

  /**
   * Calculates reference map for a file.
   * @return A map: alias → referenced anchor.
   */
  public static @NotNull Map<YAMLAlias, YAMLAnchor> getResolveAliasMap(@NotNull PsiFile file) {
    return getResolveData(file).myResolveMap;
  }

  /**
   * This method is useful for completion. It calculates a special collection of anchors in a given file.
   * For every anchor name the result will contain only the first anchor with that name.
   */
  public static @NotNull Collection<YAMLAnchor> getFirstAnchorDefs(@NotNull PsiFile file) {
    return getResolveData(file).myFirstDefs;
  }

  private static @NotNull YAMLAliasResolveResult getResolveData(@NotNull PsiFile file) {
    return CachedValuesManager.getCachedValue(file, () -> {
      Map<YAMLAlias, YAMLAnchor> resolveMap = new HashMap<>();
      Map<String, YAMLAnchor> defMap = new HashMap<>();

      // store first definitions: need for completion
      Map<String, YAMLAnchor> firstDefMap = new HashMap<>();

      file.accept(new YamlRecursivePsiElementVisitor() {
        @Override
        public void visitAnchor(@NotNull YAMLAnchor anchor) {
          defMap.put(anchor.getName(), anchor);
          firstDefMap.putIfAbsent(anchor.getName(), anchor);
        }
        @Override
        public void visitAlias(@NotNull YAMLAlias alias) {
          String name = alias.getAliasName();
          YAMLAnchor anchor = defMap.get(name);
          if (anchor != null) {
            resolveMap.put(alias, anchor);
          }
        }
      });
      Set<YAMLAnchor> firstDefs = new HashSet<>(firstDefMap.values());
      YAMLAliasResolveResult result = new YAMLAliasResolveResult(resolveMap, firstDefs);
      return CachedValueProvider.Result.create(result, file);
    });
  }

  private static class YAMLAliasResolveResult {
    final @NotNull Map<YAMLAlias, YAMLAnchor> myResolveMap;
    final @NotNull Set<YAMLAnchor> myFirstDefs;

    YAMLAliasResolveResult(@NotNull Map<YAMLAlias, YAMLAnchor> map, @NotNull Set<YAMLAnchor> firstDefs) {
      myResolveMap = Collections.unmodifiableMap(map);
      myFirstDefs = Collections.unmodifiableSet(firstDefs);
    }
  }
}
