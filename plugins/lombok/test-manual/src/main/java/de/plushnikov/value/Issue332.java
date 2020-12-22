package de.plushnikov.value;

import lombok.Value;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Issue332 {

  @Value(staticConstructor = "of")
  private static class Pair<Left, Right> {

    private final Left left;
    private final Right right;
  }

  private static final Collection<Pair<Pattern, Function<Matcher, String>>> PATTERNS = Collections.singletonList(Pair.of(
    Pattern.compile("FOREIGN_KEY", Pattern.CASE_INSENSITIVE),
    matcher -> matcher.group(1)));


  @Value(staticConstructor = "of")
  private static class Some<T, X> {

    private final T fieldOfSome;
    private final X fieldOfElse;
  }

  public static void main(String[] args) {
    Some<Function<Matcher, String>, Long> test = Some.<Function<Matcher, String>, Long>of(
      matcher -> matcher.group(1), 2L);

    Some<Function<Matcher, String>, String> test2 = Some.of(
      matcher -> matcher.group(1), "");
  }
}
