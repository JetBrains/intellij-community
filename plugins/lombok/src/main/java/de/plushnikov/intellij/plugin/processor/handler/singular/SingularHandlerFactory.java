package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SingularHandlerFactory {

  private static final String JAVA_LANG_ITERABLE = CommonClassNames.JAVA_LANG_ITERABLE;
  private static final String JAVA_UTIL_COLLECTION = CommonClassNames.JAVA_UTIL_COLLECTION;
  private static final String JAVA_UTIL_LIST = CommonClassNames.JAVA_UTIL_LIST;
  private static final String[] JAVA_MAPS = new String[]{CommonClassNames.JAVA_UTIL_MAP, "java.util.SortedMap", "java.util.NavigableMap",};
  private static final String[] JAVA_SETS = new String[]{CommonClassNames.JAVA_UTIL_SET, "java.util.SortedSet", "java.util.NavigableSet"};
  private static final String[] GUAVE_COLLECTIONS = new String[]{"com.google.common.collect.ImmutableCollection", "com.google.common.collect.ImmutableList"};
  private static final String[] GUAVA_SETS = new String[]{"com.google.common.collect.ImmutableSet", "com.google.common.collect.ImmutableSortedSet"};
  private static final String[] GUAVA_MAPS = new String[]{"com.google.common.collect.ImmutableMap", "com.google.common.collect.ImmutableBiMap", "com.google.common.collect.ImmutableSortedMap"};

  private static final Set<String> COLLECTION_TYPES = new HashSet<String>() {{
    addAll(toSet(JAVA_LANG_ITERABLE, JAVA_UTIL_COLLECTION, JAVA_UTIL_LIST));
    addAll(toSet(JAVA_SETS));
  }};

  private static final Set<String> GUAVA_COLLECTION_TYPES = new HashSet<String>() {{
    addAll(toSet(GUAVE_COLLECTIONS));
    addAll(toSet(GUAVA_SETS));
  }};

  private static final Set<String> MAP_TYPES = new HashSet<String>() {{
    addAll(toSet(JAVA_MAPS));
  }};
  private static final Set<String> GUAVA_MAP_TYPES = new HashSet<String>() {{
    addAll(toSet(GUAVA_MAPS));
  }};
  private static final Set<String> VALID_SINGULAR_TYPES = new HashSet<String>() {{
    addAll(COLLECTION_TYPES);

    addAll(toSet(JAVA_MAPS));
    addAll(toSet(GUAVE_COLLECTIONS));
    addAll(toSet(GUAVA_SETS));
    addAll(toSet(GUAVA_MAPS));
  }};

  private static Set<String> toSet(String... from) {
    return new HashSet<String>(Arrays.asList(from));
  }

  public static boolean isInvalidSingularType(@Nullable String qualifiedName) {
    return qualifiedName == null || !VALID_SINGULAR_TYPES.contains(qualifiedName);
  }

  @NotNull
  public static BuilderElementHandler getHandlerFor(@NotNull PsiVariable psiVariable, @Nullable PsiAnnotation singularAnnotation) {
    if (null == singularAnnotation) {
      return new NonSingularHandler();
    }

    final PsiType psiType = psiVariable.getType();
    final String qualifiedName = PsiTypeUtil.getQualifiedName(psiType);
    if (!isInvalidSingularType(qualifiedName)) {
      if (COLLECTION_TYPES.contains(qualifiedName)) {
        return new SingularCollectionHandler();
      }
      if (MAP_TYPES.contains(qualifiedName)) {
        return new SingularMapHandler();
      }
      if (GUAVA_COLLECTION_TYPES.contains(qualifiedName)) {
        return new SingularGuavaCollectionHandler(qualifiedName, qualifiedName.contains("Sorted"));
      }
      if (GUAVA_MAP_TYPES.contains(qualifiedName)) {
        return new SingularGuavaMapHandler(qualifiedName, qualifiedName.contains("Sorted"));
      }
    }
    return new EmptyBuilderElementHandler();
  }
}
