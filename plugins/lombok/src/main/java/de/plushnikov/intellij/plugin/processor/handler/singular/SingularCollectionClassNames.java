package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.psi.CommonClassNames;

interface SingularCollectionClassNames {
  String GUAVA_IMMUTABLE_COLLECTION = "com.google.common.collect.ImmutableCollection";
  String GUAVA_IMMUTABLE_LIST = "com.google.common.collect.ImmutableList";
  String[] GUAVA_COLLECTIONS = new String[]{
    GUAVA_IMMUTABLE_COLLECTION, GUAVA_IMMUTABLE_LIST
  };

  String GUAVA_IMMUTABLE_SET = "com.google.common.collect.ImmutableSet";
  String GUAVA_IMMUTABLE_SORTED_SET = "com.google.common.collect.ImmutableSortedSet";
  String[] GUAVA_SETS = new String[]{
    GUAVA_IMMUTABLE_SET, GUAVA_IMMUTABLE_SORTED_SET
  };

  String GUAVA_IMMUTABLE_MAP = "com.google.common.collect.ImmutableMap";
  String GUAVA_IMMUTABLE_TABLE = "com.google.common.collect.ImmutableTable";
  String[] GUAVA_TABLE = new String[]{GUAVA_IMMUTABLE_TABLE};

  String GUAVA_IMMUTABLE_BI_MAP = "com.google.common.collect.ImmutableBiMap";
  String GUAVA_IMMUTABLE_SORTED_MAP = "com.google.common.collect.ImmutableSortedMap";
  String[] GUAVA_MAPS = new String[]{
    GUAVA_IMMUTABLE_MAP,
    GUAVA_IMMUTABLE_BI_MAP,
    GUAVA_IMMUTABLE_SORTED_MAP
  };

  String JAVA_LANG_ITERABLE = CommonClassNames.JAVA_LANG_ITERABLE;
  String JAVA_UTIL_COLLECTION = CommonClassNames.JAVA_UTIL_COLLECTION;
  String JAVA_UTIL_LIST = CommonClassNames.JAVA_UTIL_LIST;

  String JAVA_UTIL_MAP = CommonClassNames.JAVA_UTIL_MAP;
  String JAVA_UTIL_SORTED_MAP = CommonClassNames.JAVA_UTIL_SORTED_MAP;
  String JAVA_UTIL_NAVIGABLE_MAP = CommonClassNames.JAVA_UTIL_NAVIGABLE_MAP;
  String[] JAVA_MAPS = new String[]{
    JAVA_UTIL_MAP,
    JAVA_UTIL_SORTED_MAP,
    JAVA_UTIL_NAVIGABLE_MAP
  };

  String JAVA_UTIL_SET = CommonClassNames.JAVA_UTIL_SET;
  String JAVA_UTIL_SORTED_SET = CommonClassNames.JAVA_UTIL_SORTED_SET;
  String JAVA_UTIL_NAVIGABLE_SET = CommonClassNames.JAVA_UTIL_NAVIGABLE_SET;
  String[] JAVA_SETS = new String[]{
    JAVA_UTIL_SET,
    JAVA_UTIL_SORTED_SET,
    JAVA_UTIL_NAVIGABLE_SET
  };
}
