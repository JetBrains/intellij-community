package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.psi.CommonClassNames;

interface SingularCollectionClassNames {
  String GUAVA_IMMUTABLE_COLLECTION = "com.google.common.collect.ImmutableCollection";
  String GUAVA_IMMUTABLE_LIST = "com.google.common.collect.ImmutableList";
  String GUAVA_IMMUTABLE_SET = "com.google.common.collect.ImmutableSet";
  String GUAVA_IMMUTABLE_SORTED_SET = "com.google.common.collect.ImmutableSortedSet";
  String GUAVA_IMMUTABLE_MAP = "com.google.common.collect.ImmutableMap";
  String GUAVA_IMMUTABLE_TABLE = "com.google.common.collect.ImmutableTable";
  String GUAVA_IMMUTABLE_BI_MAP = "com.google.common.collect.ImmutableBiMap";
  String GUAVA_IMMUTABLE_SORTED_MAP = "com.google.common.collect.ImmutableSortedMap";

  String JAVA_LANG_ITERABLE = CommonClassNames.JAVA_LANG_ITERABLE;
  String JAVA_UTIL_COLLECTION = CommonClassNames.JAVA_UTIL_COLLECTION;
  String JAVA_UTIL_LIST = CommonClassNames.JAVA_UTIL_LIST;

  String JAVA_UTIL_MAP = CommonClassNames.JAVA_UTIL_MAP;
  String JAVA_UTIL_SORTED_MAP = "java.util.SortedMap";
  String JAVA_UTIL_NAVIGABLE_MAP = "java.util.NavigableMap";

  String JAVA_UTIL_SET = CommonClassNames.JAVA_UTIL_SET;
  String JAVA_UTIL_SORTED_SET = "java.util.SortedSet";
  String JAVA_UTIL_NAVIGABLE_SET = "java.util.NavigableSet";
}
