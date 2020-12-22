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

  private static final String[] JAVA_MAPS = new String[]{CommonClassNames.JAVA_UTIL_MAP, SingularCollectionClassNames.JAVA_UTIL_SORTED_MAP, SingularCollectionClassNames.JAVA_UTIL_NAVIGABLE_MAP,};
  private static final String[] JAVA_SETS = new String[]{CommonClassNames.JAVA_UTIL_SET, SingularCollectionClassNames.JAVA_UTIL_SORTED_SET, SingularCollectionClassNames.JAVA_UTIL_NAVIGABLE_SET};
  private static final String[] GUAVA_COLLECTIONS = new String[]{SingularCollectionClassNames.GUAVA_IMMUTABLE_COLLECTION, SingularCollectionClassNames.GUAVA_IMMUTABLE_LIST};
  private static final String[] GUAVA_SETS = new String[]{SingularCollectionClassNames.GUAVA_IMMUTABLE_SET, SingularCollectionClassNames.GUAVA_IMMUTABLE_SORTED_SET};
  private static final String[] GUAVA_MAPS = new String[]{SingularCollectionClassNames.GUAVA_IMMUTABLE_MAP, SingularCollectionClassNames.GUAVA_IMMUTABLE_BI_MAP, SingularCollectionClassNames.GUAVA_IMMUTABLE_SORTED_MAP};
  private static final String[] GUAVA_TABLE = new String[]{SingularCollectionClassNames.GUAVA_IMMUTABLE_TABLE};

  private static final Set<String> COLLECTION_TYPES = new HashSet<String>() {{
    addAll(toSet(SingularCollectionClassNames.JAVA_LANG_ITERABLE, SingularCollectionClassNames.JAVA_UTIL_COLLECTION, SingularCollectionClassNames.JAVA_UTIL_LIST));
    addAll(toSet(JAVA_SETS));
  }};

  private static final Set<String> GUAVA_COLLECTION_TYPES = new HashSet<String>() {{
    addAll(toSet(GUAVA_COLLECTIONS));
    addAll(toSet(GUAVA_SETS));
  }};

  private static final Set<String> MAP_TYPES = new HashSet<String>() {{
    addAll(toSet(JAVA_MAPS));
  }};
  private static final Set<String> GUAVA_MAP_TYPES = new HashSet<String>() {{
    addAll(toSet(GUAVA_MAPS));
  }};
  private static final Set<String> GUAVA_TABLE_TYPES = new HashSet<String>() {{
    addAll(toSet(GUAVA_TABLE));
  }};
  private static final Set<String> VALID_SINGULAR_TYPES = new HashSet<String>() {{
    addAll(COLLECTION_TYPES);
    addAll(MAP_TYPES);
    addAll(GUAVA_COLLECTION_TYPES);
    addAll(GUAVA_MAP_TYPES);
    addAll(GUAVA_TABLE_TYPES);
  }};

  private static Set<String> toSet(String... from) {
    return new HashSet<>(Arrays.asList(from));
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
        return new SingularCollectionHandler(qualifiedName);
      }
      if (MAP_TYPES.contains(qualifiedName)) {
        return new SingularMapHandler(qualifiedName);
      }
      if (GUAVA_COLLECTION_TYPES.contains(qualifiedName)) {
        return new SingularGuavaCollectionHandler(qualifiedName, qualifiedName.contains("Sorted"));
      }
      if (GUAVA_MAP_TYPES.contains(qualifiedName)) {
        return new SingularGuavaMapHandler(qualifiedName, qualifiedName.contains("Sorted"));
      }
      if (GUAVA_TABLE_TYPES.contains(qualifiedName)) {
        return new SingularGuavaTableHandler(qualifiedName, false);
      }
    }
    return new EmptyBuilderElementHandler();
  }
}

