package de.plushnikov.val;

import com.google.common.collect.Collections2;
import lombok.val;

import java.util.*;

public class Issue260 {

  private static Map<String, String> TYPE_ID_MAPPINGS = new HashMap<>();
  private static Set<String> MULTIPLE_SEARCH_TYPES = new HashSet<>();

  private static String[] toStringArray(Collection<String> source) {
    return source.stream().toArray(String[]::new);
  }

  private static boolean isTrue() {
    return true;
  }

  private static String[] getSearchTypes(String type) {
    val result = TYPE_ID_MAPPINGS.containsKey(type) ? newHashSet(TYPE_ID_MAPPINGS.get(type)) : MULTIPLE_SEARCH_TYPES;
    result.add("test3");

    return toStringArray(result);
  }

  public static <E> Collection<E> newHashSet(E foo) {
    return new HashSet<>();
  }

  static Set<String> newHashSet2(String foo) {
    return new HashSet<>();
  }



  private static class Bucket {
    public String getKeyAsText() {
      return "key";
    }
  }

  private static class Aggs {
    public List<Bucket> getBuckets() {
      return Collections.emptyList();
    }
  }

  public static Collection<String> testTransform(Aggs aggs) {
    val aggsTransform = Collections2.transform(aggs.getBuckets(), bucket -> bucket.getKeyAsText());
    return aggsTransform;
  }

}
