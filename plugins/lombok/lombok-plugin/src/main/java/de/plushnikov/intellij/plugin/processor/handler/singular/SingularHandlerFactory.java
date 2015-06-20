package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class SingularHandlerFactory {

  private static final String JAVA_LANG_ITERABLE = CommonClassNames.JAVA_LANG_ITERABLE;
  private static final String JAVA_UTIL_COLLECTION = CommonClassNames.JAVA_UTIL_COLLECTION;
  private static final String JAVA_UTIL_LIST = CommonClassNames.JAVA_UTIL_LIST;
  private static final String[] JAVA_MAPS = new String[]{CommonClassNames.JAVA_UTIL_MAP, "java.util.SortedMap", "java.util.NavigableMap",};
  private static final String[] JAVA_SETS = new String[]{CommonClassNames.JAVA_UTIL_SET, "java.util.SortedSet", "java.util.NavigableSet"};
  private static final String[] GUAVE_COLLECTIONS = new String[]{"com.google.common.collect.ImmutableCollection", "com.google.common.collect.ImmutableList"};
  private static final String[] GUAVA_SETS = new String[]{"com.google.common.collect.ImmutableSet", "com.google.common.collect.ImmutableSortedSet"};
  private static final String[] GUAVA_MAPS = new String[]{"com.google.common.collect.ImmutableMap", "com.google.common.collect.ImmutableBiMap", "com.google.common.collect.ImmutableSortedMap"};

  private static final Map<String, String> COLLECTION_TYPES = new HashMap<String, String>() {{
    putAll(toShortNames(JAVA_LANG_ITERABLE, JAVA_UTIL_COLLECTION, JAVA_UTIL_LIST));
    putAll(toShortNames(JAVA_SETS));
  }};

  private static final Map<String, String> GUAVA_COLLECTION_TYPES = new HashMap<String, String>() {{
    putAll(toShortNames(GUAVE_COLLECTIONS));
    putAll(toShortNames(GUAVA_SETS));
  }};

  private static final Map<String, String> MAP_TYPES = new HashMap<String, String>() {{
    putAll(toShortNames(JAVA_MAPS));
  }};
  private static final Map<String, String> GUAVA_MAP_TYPES = new HashMap<String, String>() {{
    putAll(toShortNames(GUAVA_MAPS));
  }};
  private static final Map<String, String> VALID_SINGULAR_TYPES = new HashMap<String, String>() {{
    putAll(COLLECTION_TYPES);

    putAll(toShortNames(JAVA_MAPS));
    putAll(toShortNames(JAVA_SETS));
    putAll(toShortNames(GUAVE_COLLECTIONS));
    putAll(toShortNames(GUAVA_SETS));
    putAll(toShortNames(GUAVA_MAPS));
  }};

  private static Map<String, String> toShortNames(String... from) {
    final Map<String, String> result = new HashMap<String, String>();
    for (String string : from) {
      result.put(StringUtil.getShortName(string), string);
      result.put(string, string);
    }
    return result;
  }

  @NotNull
  public static AbstractSingularHandler getHandlerFor(@NotNull PsiVariable psiVariable, @Nullable PsiAnnotation singularAnnotation) {
    if (null == singularAnnotation) {
      return new NonSingularHandler();
    }

    final PsiType psiType = psiVariable.getType();

    final String qualifiedName = PsiTypeUtil.getQualifiedName(psiType);
    if (qualifiedName == null || !VALID_SINGULAR_TYPES.containsKey(qualifiedName)) {
      //TODO add Error "Lombok does not know how to create the singular-form builder methods for type '" + qualifiedName + "'; they won't be generated."
      return new NonSingularHandler();
    }

    if (COLLECTION_TYPES.containsKey(qualifiedName)) {
      return new SingularCollectionHandler();
    }
    if (MAP_TYPES.containsKey(qualifiedName)) {
      return new SingularMapHandler();
    }
    if (GUAVA_COLLECTION_TYPES.containsKey(qualifiedName)) {
      return new SingularGuavaCollectionHandler(GUAVA_COLLECTION_TYPES.get(qualifiedName), qualifiedName.contains("Sorted"));
    }
    if (GUAVA_MAP_TYPES.containsKey(qualifiedName)) {
      return new SingularGuavaMapHandler(GUAVA_MAP_TYPES.get(qualifiedName), qualifiedName.contains("Sorted"));
    }

    return new NonSingularHandler();
  }
}
