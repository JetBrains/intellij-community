package slowCheck;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static slowCheck.Generator.*;

/**
 * @author peter
 */
public class GeneratorTest extends PropertyCheckerTestCase {
  
  public void testMod() {
    checkFalsified(integers(),
                   i -> i % 12 != 0,
                   1);
  }

  public void testListSumMod() {
    checkFalsified(nonEmptyLists(integers()),
                   l -> l.stream().mapToInt(Integer::intValue).sum() % 10 != 0,
                   7);
  }

  public void testListContainsDivisible() {
    checkFalsified(nonEmptyLists(integers()),
                   l -> l.stream().allMatch(i -> i % 10 != 0),
                   3);
  }

  public void testStringContains() {
    PropertyFailure<String> failure = checkFalsified(stringsOf(asciiPrintableChars()),
                                               s -> !s.contains("a"),
                                               17);
    assertEquals("a", failure.getMinimalCounterexample().getExampleValue());
  }

  public void testLetterStringContains() {
    checkFalsified(stringsOf(asciiLetters()),
                   s -> !s.contains("a"),
                   3);
  }
  
  public void testIsSorted() {
    PropertyFailure<List<Integer>> failure = checkFalsified(nonEmptyLists(integers()),
                                                            l -> l.stream().sorted().collect(Collectors.toList()).equals(l),
                                                            35);
    List<Integer> value = failure.getMinimalCounterexample().getExampleValue();
    assertEquals(2, value.size());
    assertTrue(value.toString(), value.stream().allMatch(i -> Math.abs(i) < 2));
  }

  public void testSuccess() {
    PropertyChecker.forAll(listsOf(integers(-1, 1))).shouldHold(l -> l.stream().allMatch(i -> Math.abs(i) <= 1));
  }

  public void testSortedDoublesNonDescending() {
    PropertyFailure<List<Double>> failure = checkFalsified(listsOf(doubles()),
                                                           l -> isSorted(l.stream().sorted().collect(Collectors.toList())),
                                                           58);
    assertEquals(2, failure.getMinimalCounterexample().getExampleValue().size());
  }

  private static boolean isSorted(List<Double> list) {
    for (int i = 0; i < list.size() - 1; i++) {
      double d1 = list.get(i);
      double d2 = list.get(i + 1);
      if (!(d1 <= d2)) return false;
    }
    return true;
  }

  public void testSuchThat() {
    PropertyChecker.forAll(integers().suchThat(i -> i < 0)).shouldHold(i -> i < 0);
  }

  public void testStringOfStringChecksAllChars() {
    checkFalsified(stringsOf("abc "),
                   s -> !s.contains(" "),
                   4);
  }

  public void testLongListsHappen() {
    PropertyFailure<List<Integer>> failure = checkFalsified(listsOf(integers()),
                                                            l -> l.size() < 200,
                                                            419);
    assertEquals(200, failure.getMinimalCounterexample().getExampleValue().size());
  }

  public void testNonEmptyList() {
    PropertyChecker.forAll(nonEmptyLists(integers())).shouldHold(l -> !l.isEmpty());
  }

  public void testNoDuplicateData() {
    Set<List<Integer>> visited = new HashSet<>();
    PropertyChecker.forAll(listsOf(integers())).shouldHold(l -> visited.add(l));
  }

  public void testOneOf() {
    List<Integer> values = new ArrayList<>();
    PropertyChecker.forAll(anyOf(integers(0, 1), integers(10, 1100))).shouldHold(i -> values.add(i));
    assertTrue(values.stream().anyMatch(i -> i < 2));
    assertTrue(values.stream().anyMatch(i -> i > 5));
  }

  public void testAsciiIdentifier() {
    PropertyChecker.forAll(asciiIdentifiers())
      .shouldHold(s -> Character.isJavaIdentifierStart(s.charAt(0)) && s.chars().allMatch(Character::isJavaIdentifierPart));
    checkFalsified(asciiIdentifiers(),
                   s -> !s.contains("_"),
                   1);
  }

  public void testBoolean() {
    PropertyFailure<List<Boolean>> failure = checkFalsified(listsOf(booleans()),
                                                            l -> !l.contains(true) || !l.contains(false),
                                                            2);
    assertEquals(2, failure.getMinimalCounterexample().getExampleValue().size());
  }

  public void testShrinkingNonEmptyList() {
    PropertyFailure<List<Integer>> failure = checkFalsified(nonEmptyLists(integers(0, 100)),
                                                            l -> !l.contains(42),
                                                            4);
    assertEquals(1, failure.getMinimalCounterexample().getExampleValue().size());
  }

  public void testRecheckWithGivenSeeds() {
    Generator<List<Integer>> gen = nonEmptyLists(integers(0, 100));
    Predicate<List<Integer>> property = l -> !l.contains(42);

    PropertyFailure<?> failure = checkFails(PropertyChecker.forAll(gen), property).getFailure();
    assertTrue(failure.getIterationNumber() > 1);

    PropertyFalsified e;

    e = checkFails(PropertyChecker.forAll(gen).rechecking(failure.getIterationSeed(), failure.getSizeHint()), property);
    assertEquals(1, e.getFailure().getIterationNumber());

    e = checkFails(PropertyChecker.forAll(gen).withSeed(failure.getGlobalSeed()), property);
    assertEquals(failure.getIterationNumber(), e.getFailure().getIterationNumber());
  }

  public void testShrinkingComplexString() {
    checkFalsified(listsOf(stringsOf(asciiPrintableChars())),
                   l -> {
                     String s = l.toString();
                     return !"abcdefghijklmnopqrstuvwxyz()[]#!".chars().allMatch(c -> s.indexOf((char)c) >= 0);
                   },
                   153);
  }

}
