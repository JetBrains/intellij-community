package slowCheck;

import junit.framework.TestCase;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static slowCheck.GenChar.*;
import static slowCheck.GenCollection.*;
import static slowCheck.GenNumber.*;
import static slowCheck.GenString.*;

/**
 * @author peter
 */
public class GeneratorTest extends TestCase {
  private static final CheckerSettings ourTestSettings = CheckerSettings.DEFAULT_SETTINGS.withSeed(0);
  
  public void testMod() {
    checkFalsified(integers(), 
                   i -> i % 12 != 0,
                   3);
  }

  public void testListSumMod() {
    checkFalsified(listOf(integers()), 
                   l -> l.stream().mapToInt(Integer::intValue).sum() % 10 != 0,
                   130);
  }

  public void testListContainsDivisible() {
    checkFalsified(listOf(integers()), 
                   l -> l.stream().allMatch(i -> i % 10 != 0),
                   14);
  }

  public void testStringContains() {
    checkFalsified(stringOf(asciiPrintable()), 
                   s -> !s.contains("a"),
                   7);
  }

  public void testLetterStringContains() {
    checkFalsified(stringOf(asciiLetter()), 
                   s -> !s.contains("a"),
                   8);
  }
  
  public void testIsSorted() {
    checkFalsified(listOf(integers()), 
                   l -> l.stream().sorted().collect(Collectors.toList()).equals(l),
                   217);
  }

  public void testSuccess() {
    PropertyChecker.forAll(listOf(integers(-1, 1)), 
                   l -> l.stream().allMatch(i -> Math.abs(i) <= 1));
  }

  public void testSortedDoublesNonDescending() {
    checkFalsified(listOf(doubles()), 
                   l -> isSorted(l.stream().sorted().collect(Collectors.toList())),
                   297);
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
    }, 32);
  }

  public void testSuchThat() {
    PropertyChecker.forAll(integers(-1, 1).suchThat(i -> i == 0), i -> i == 0);
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
                   7);
  }

  private <T> void checkFalsified(Generator<T> generator, Predicate<T> predicate, int minimizationSteps) {
    try {
      PropertyChecker.forAll(ourTestSettings, generator, predicate);
      fail("Can't falsify " + getName());
    }
    catch (PropertyFalsified e) {
      System.out.println(" " + getName());
      System.out.println("Value: " + e.getBreakingValue());
      System.out.println("Data: " + e.getData());
      assertEquals(minimizationSteps, e.getFailure().getTotalMinimizationStepCount());
      assertEquals(e.getBreakingValue(), generator.generateUnstructured(e.getData()));
    }
  }
}
