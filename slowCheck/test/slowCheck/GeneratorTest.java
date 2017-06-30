package slowCheck;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
                   9);
  }

  public void testStringContains() {
    checkFalsified(stringsOf(asciiPrintableChars()),
                   s -> !s.contains("a"),
                   10);
  }

  public void testLetterStringContains() {
    checkFalsified(stringsOf(asciiLetters()),
                   s -> !s.contains("a"),
                   5);
  }
  
  public void testIsSorted() {
    PropertyFailure<List<Integer>> failure = checkFalsified(nonEmptyLists(integers()),
                                                            l -> l.stream().sorted().collect(Collectors.toList()).equals(l),
                                                            69);
    assertEquals(2, failure.getMinimalCounterexample().getExampleValue().size());
  }

  public void testSuccess() {
    PropertyChecker.forAll(listsOf(integers(-1, 1)),
                           l -> l.stream().allMatch(i -> Math.abs(i) <= 1));
  }

  public void testSortedDoublesNonDescending() {
    PropertyFailure<List<Double>> failure = checkFalsified(listsOf(doubles()),
                                                           l -> isSorted(l.stream().sorted().collect(Collectors.toList())),
                                                           141);
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
    PropertyChecker.forAll(integers().suchThat(i -> i < 0), i -> i < 0);
  }

  public void testStringOfStringChecksAllChars() {
    checkFalsified(stringsOf("abc "),
                   s -> !s.contains(" "),
                   3);
  }

  public void testLongListsHappen() {
    PropertyFailure<List<Integer>> failure = checkFalsified(listsOf(integers()),
                                                            l -> l.size() < 200,
                                                            631);
    assertEquals(200, failure.getMinimalCounterexample().getExampleValue().size());
  }

  public void testNonEmptyList() {
    PropertyChecker.forAll(nonEmptyLists(integers()), l -> !l.isEmpty());
  }

  public void testNoDuplicateData() {
    Set<List<Integer>> visited = new HashSet<>();
    PropertyChecker.forAll(listsOf(integers()), l -> visited.add(l));
  }

  public void testOneOf() {
    List<Integer> values = new ArrayList<>();
    PropertyChecker.forAll(anyOf(integers(0, 1), integers(10, 1100)), i -> values.add(i));
    assertTrue(values.stream().anyMatch(i -> i < 2));
    assertTrue(values.stream().anyMatch(i -> i > 5));
  }

  public void testAsciiIdentifier() {
    PropertyChecker.forAll(asciiIdentifiers(),
                           s -> Character.isJavaIdentifierStart(s.charAt(0)) && s.chars().allMatch(Character::isJavaIdentifierPart));
    checkFalsified(asciiIdentifiers(),
                   s -> !s.contains("_"),
                   1);
  }

  public void testBoolean() {
    PropertyFailure<List<Boolean>> failure = checkFalsified(listsOf(booleans()),
                                                            l -> !l.contains(true) || !l.contains(false),
                                                            4);
    assertEquals(2, failure.getMinimalCounterexample().getExampleValue().size());
  }

  public void testShrinkingNonEmptyList() {
    PropertyFailure<List<Integer>> failure = checkFalsified(nonEmptyLists(integers(0, 100)),
                                                            l -> !l.contains(42),
                                                            10);
    assertEquals(1, failure.getMinimalCounterexample().getExampleValue().size());
  }

}
