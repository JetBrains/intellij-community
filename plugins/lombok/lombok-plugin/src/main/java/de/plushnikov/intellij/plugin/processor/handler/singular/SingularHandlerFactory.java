package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import static java.util.Arrays.asList;

public class SingularHandlerFactory {

  private static final String JAVA_LANG_ITERABLE = CommonClassNames.JAVA_LANG_ITERABLE;
  private static final String JAVA_UTIL_COLLECTION = CommonClassNames.JAVA_UTIL_COLLECTION;
  private static final String JAVA_UTIL_LIST = CommonClassNames.JAVA_UTIL_LIST;
  private static final String[] JAVA_MAPS = new String[]{CommonClassNames.JAVA_UTIL_MAP, "java.util.SortedMap", "java.util.NavigableMap",};
  private static final String[] JAVA_SETS = new String[]{CommonClassNames.JAVA_UTIL_SET, "java.util.SortedSet", "java.util.NavigableSet"};
  private static final String[] GUAVE_COLLECTIONS = new String[]{"com.google.common.collect.ImmutableCollection", "com.google.common.collect.ImmutableList"};
  private static final String[] GUAVA_SETS = new String[]{"com.google.common.collect.ImmutableSet", "com.google.common.collect.ImmutableSortedSet"};
  private static final String[] GUAVA_MAPS = new String[]{"com.google.common.collect.ImmutableMap", "com.google.common.collect.ImmutableBiMap", "com.google.common.collect.ImmutableSortedMap"};

  private static final Collection<String> COLLECTION_TYPES = Collections.unmodifiableSet(new HashSet<String>() {{
    add(JAVA_LANG_ITERABLE);
    add(JAVA_UTIL_COLLECTION);
    add(JAVA_UTIL_LIST);
    addAll(asList(JAVA_SETS));
  }});
  private static final Collection<String> GUAVA_COLLECTION_TYPES = Collections.unmodifiableSet(new HashSet<String>() {{
    addAll(asList(GUAVE_COLLECTIONS));
    addAll(asList(GUAVA_SETS));
  }});
  private static final Collection<String> MAP_TYPES = Collections.unmodifiableSet(new HashSet<String>() {{
    addAll(asList(JAVA_MAPS));
  }});
  private static final Collection<String> GUAVA_MAP_TYPES = Collections.unmodifiableSet(new HashSet<String>() {{
    addAll(asList(GUAVA_MAPS));
  }});

  private static final Collection<String> VALID_SINGULAR_TYPES = Collections.unmodifiableSet(new HashSet<String>() {{
    add(JAVA_LANG_ITERABLE);
    add(JAVA_UTIL_COLLECTION);
    add(JAVA_UTIL_LIST);
    addAll(asList(JAVA_MAPS));
    addAll(asList(JAVA_SETS));
    addAll(asList(GUAVE_COLLECTIONS));
    addAll(asList(GUAVA_SETS));
    addAll(asList(GUAVA_MAPS));
  }});

  @NotNull
  public static AbstractSingularHandler getHandlerFor(@NotNull PsiVariable psiVariable, @Nullable PsiAnnotation singularAnnotation) {
    if (null == singularAnnotation) {
      return new NonSingularHandler();
    }

    final PsiType psiFieldType = psiVariable.getType();

    final String qualifiedName = PsiTypeUtil.getQualifiedName(psiFieldType);
    if (qualifiedName == null || !VALID_SINGULAR_TYPES.contains(qualifiedName)) {
      //TODO add Error "Lombok does not know how to create the singular-form builder methods for type '" + qualifiedName + "'; they won't be generated."
      return new NonSingularHandler();
    }

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

    return new NonSingularHandler();
  }
}
