package slowCheck;

import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static slowCheck.Generator.*;
import static slowCheck.Generator.doubles;
import static slowCheck.Generator.listOf;

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
                   110);
  }

  public void testListContainsDivisible() {
    checkFalsified(listOf(integers()), 
                   l -> l.stream().allMatch(i -> i % 10 != 0),
                   13);
  }

  public void testStringContains() {
    checkFalsified(stringOf(asciiPrintableChar()), 
                   s -> !s.contains("a"),
                   10);
  }

  public void testLetterStringContains() {
    checkFalsified(stringOf(asciiLetterChar()), 
                   s -> !s.contains("a"),
                   8);
  }
  
  public void testIsSorted() {
    checkFalsified(listOf(integers()), 
                   l -> l.stream().sorted().collect(Collectors.toList()).equals(l),
                   197);
  }

  public void testSuccess() {
    Checker.forAll(ourTestSettings, listOf(integers(-1, 1)), l -> 
      l.stream().allMatch(i -> Math.abs(i) <= 1));
  }

  public void testSortedDoublesNonDescending() {
    checkFalsified(listOf(doubles()), l -> isSorted(ContainerUtil.sorted(l)), 329);
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
    Checker.forAll(ourTestSettings, integers(-1, 1).suchThat(i -> i == 0), i -> i == 0);
  }

  public void testUnsatisfiableSuchThat() {
    try {
      Checker.forAll(ourTestSettings, integers(-1, 1).suchThat(i -> i >2), i -> i == 0);
      fail();
    }
    catch (CannotSatisfyCondition ignored) {
    }
  }

  private <T> void checkFalsified(Generator<T> generator, Predicate<T> predicate, int minimizationSteps) {
    try {
      Checker.forAll(ourTestSettings, generator, predicate);
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
