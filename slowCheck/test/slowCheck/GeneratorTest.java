package slowCheck;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static slowCheck.GenBoolean.bool;
import static slowCheck.GenChar.asciiLetter;
import static slowCheck.GenChar.asciiPrintable;
import static slowCheck.GenCollection.listOf;
import static slowCheck.GenCollection.nonEmptyListOf;
import static slowCheck.GenNumber.doubles;
import static slowCheck.GenNumber.integers;
import static slowCheck.GenString.stringOf;

/**
 * @author peter
 */
public class GeneratorTest extends TestCase {
  private static final CheckerSettings ourTestSettings = CheckerSettings.DEFAULT_SETTINGS.withSeed(0);
  
  public void testMod() {
    checkFalsified(integers(),
                   i -> i % 12 != 0,
                   1);
  }

  public void testListSumMod() {
    checkFalsified(nonEmptyListOf(integers()),
                   l -> l.stream().mapToInt(Integer::intValue).sum() % 10 != 0,
                   7);
  }

  public void testListContainsDivisible() {
    checkFalsified(nonEmptyListOf(integers()),
                   l -> l.stream().allMatch(i -> i % 10 != 0),
                   9);
  }

  public void testStringContains() {
    checkFalsified(stringOf(asciiPrintable()),
                   s -> !s.contains("a"),
                   10);
  }

  public void testLetterStringContains() {
    checkFalsified(stringOf(asciiLetter()),
                   s -> !s.contains("a"),
                   5);
  }
  
  public void testIsSorted() {
    PropertyFailure<List<Integer>> failure = checkFalsified(nonEmptyListOf(integers()),
                                                            l -> l.stream().sorted().collect(Collectors.toList()).equals(l),
                                                            69);
    assertEquals(2, failure.getMinimalCounterexample().getExampleValue().size());
  }

  public void testSuccess() {
    PropertyChecker.forAll(listOf(integers(-1, 1)),
                           l -> l.stream().allMatch(i -> Math.abs(i) <= 1));
  }

  public void testSortedDoublesNonDescending() {
    PropertyFailure<List<Double>> failure = checkFalsified(listOf(doubles()),
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

  public void testPropertyThrowsException() {
    checkFalsified(integers(), p -> {
      throw new AssertionError(p);
    }, 1);
  }

  public void testSuchThat() {
    PropertyChecker.forAll(integers().suchThat(i -> i < 0), i -> i < 0);
  }

  public void testUnsatisfiableSuchThat() {
    try {
      PropertyChecker.forAll(integers(-1, 1).suchThat(i -> i > 2), i -> i == 0);
      fail();
    }
    catch (CannotSatisfyCondition ignored) {
    }
  }

  public void testStringOfStringChecksAllChars() {
    checkFalsified(stringOf("abc "),
                   s -> !s.contains(" "),
                   3);
  }

  public void testLongListsHappen() {
    PropertyFailure<List<Integer>> failure = checkFalsified(listOf(integers()),
                                                            l -> l.size() < 200,
                                                            631);
    assertEquals(200, failure.getMinimalCounterexample().getExampleValue().size());
  }

  public void testNonEmptyList() {
    PropertyChecker.forAll(nonEmptyListOf(integers()), l -> !l.isEmpty());
  }

  public void testNoDuplicateData() {
    Set<List<Integer>> visited = new HashSet<>();
    PropertyChecker.forAll(listOf(integers()), l -> visited.add(l));
  }

  public void testOneOf() {
    List<Integer> values = new ArrayList<>();
    PropertyChecker.forAll(Generator.oneOf(integers(0, 1), integers(10, 1100)), i -> values.add(i));
    assertTrue(values.stream().anyMatch(i -> i < 2));
    assertTrue(values.stream().anyMatch(i -> i > 5));
  }

  public void testAsciiIdentifier() {
    PropertyChecker.forAll(GenString.asciiIdentifier(),
                           s -> Character.isJavaIdentifierStart(s.charAt(0)) && s.chars().allMatch(Character::isJavaIdentifierPart));
    checkFalsified(GenString.asciiIdentifier(),
                   s -> !s.contains("_"),
                   1);
  }

  public void testBoolean() {
    PropertyFailure<List<Boolean>> failure = checkFalsified(listOf(bool()),
                                                            l -> !l.contains(true) || !l.contains(false),
                                                            4);
    assertEquals(2, failure.getMinimalCounterexample().getExampleValue().size());
  }

  public void testShrinkingNonEmptyList() {
    PropertyFailure<List<Integer>> failure = checkFalsified(nonEmptyListOf(integers(0, 100)),
                                                            l -> !l.contains(42),
                                                            10);
    assertEquals(1, failure.getMinimalCounterexample().getExampleValue().size());
  }

  private <T> PropertyFailure<T> checkFalsified(Generator<T> generator, Predicate<T> predicate, int minimizationSteps) {
    try {
      PropertyChecker.forAll(ourTestSettings, generator, predicate);
      throw new AssertionError("Can't falsify " + getName());
    }
    catch (PropertyFalsified e) {
      //noinspection unchecked
      PropertyFailure<T> failure = (PropertyFailure<T>)e.getFailure();

      System.out.println(" " + getName());
      System.out.println("Value: " + e.getBreakingValue());
      System.out.println("Data: " + e.getData());
      assertEquals(minimizationSteps, failure.getTotalMinimizationStepCount());
      assertEquals(e.getBreakingValue(), generator.generateUnstructured(e.getData()));

      return failure;
    }
  }
}
