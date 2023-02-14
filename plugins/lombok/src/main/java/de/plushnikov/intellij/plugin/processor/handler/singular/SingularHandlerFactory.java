package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.union;
import static java.util.Set.of;

public final class SingularHandlerFactory {

  private static final Set<String> COLLECTION_TYPES =
    union(
      of(SingularCollectionClassNames.JAVA_LANG_ITERABLE,
         SingularCollectionClassNames.JAVA_UTIL_COLLECTION,
         SingularCollectionClassNames.JAVA_UTIL_LIST),
      of(SingularCollectionClassNames.JAVA_SETS));

  private static final Set<String> GUAVA_COLLECTION_TYPES =
    union(
      of(SingularCollectionClassNames.GUAVA_COLLECTIONS),
      of(SingularCollectionClassNames.GUAVA_SETS));

  private static final Set<String> MAP_TYPES = of(SingularCollectionClassNames.JAVA_MAPS);
  private static final Set<String> GUAVA_MAP_TYPES = of(SingularCollectionClassNames.GUAVA_MAPS);
  private static final Set<String> GUAVA_TABLE_TYPES = of(SingularCollectionClassNames.GUAVA_TABLE);

  private static final Set<String> VALID_SINGULAR_TYPES =
    union(COLLECTION_TYPES,
          union(
            union(MAP_TYPES, GUAVA_COLLECTION_TYPES),
            union(GUAVA_MAP_TYPES, GUAVA_TABLE_TYPES)));

  public static boolean isInvalidSingularType(@Nullable String qualifiedName) {
    return qualifiedName == null || !containsOrAnyEndsWith(VALID_SINGULAR_TYPES, qualifiedName);
  }

  private static boolean containsOrAnyEndsWith(@NotNull Set<String> elements, @NotNull String className) {
    return elements.contains(className) || ContainerUtil.exists(elements, t -> t.endsWith("." + className));
  }

  @NotNull
  public static BuilderElementHandler getHandlerFor(@NotNull PsiVariable psiVariable, boolean hasSingularAnnotation) {
    if (!hasSingularAnnotation) {
      return new NonSingularHandler();
    }

    final PsiType psiType = psiVariable.getType();
    final String qualifiedName = PsiTypeUtil.getQualifiedName(psiType);
    if (!isInvalidSingularType(qualifiedName)) {
      if (containsOrAnyEndsWith(COLLECTION_TYPES, qualifiedName)) {
        return new SingularCollectionHandler(qualifiedName);
      }
      if (containsOrAnyEndsWith(MAP_TYPES, qualifiedName)) {
        return new SingularMapHandler(qualifiedName);
      }
      if (containsOrAnyEndsWith(GUAVA_COLLECTION_TYPES, qualifiedName)) {
        return new SingularGuavaCollectionHandler(qualifiedName, qualifiedName.contains("Sorted"));
      }
      if (containsOrAnyEndsWith(GUAVA_MAP_TYPES, qualifiedName)) {
        return new SingularGuavaMapHandler(qualifiedName, qualifiedName.contains("Sorted"));
      }
      if (containsOrAnyEndsWith(GUAVA_TABLE_TYPES, qualifiedName)) {
        return new SingularGuavaTableHandler(qualifiedName, false);
      }
    }
    return new EmptyBuilderElementHandler();
  }
}

