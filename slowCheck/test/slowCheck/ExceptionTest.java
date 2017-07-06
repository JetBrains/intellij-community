package slowCheck;

import static slowCheck.Generator.*;

/**
 * @author peter
 */
public class ExceptionTest extends PropertyCheckerTestCase {

  public void testFailureReasonUnchanged() {
    try {
      PropertyChecker.forAll(ourTestSettings, integers(), i -> {
        throw new AssertionError("fail");
      });
      fail();
    }
    catch (PropertyFalsified e) {
      assertFalse(e.getMessage().contains(PropertyFalsified.FAILURE_REASON_HAS_CHANGED_DURING_MINIMIZATION));
    }
  }

  public void testFailureReasonChangedExceptionClass() {
    try {
      PropertyChecker.forAll(ourTestSettings, integers(), i -> {
        throw (i == 0 ? new RuntimeException("fail") : new IllegalArgumentException("fail"));
      });
      fail();
    }
    catch (PropertyFalsified e) {
      assertTrue(e.getMessage().contains(PropertyFalsified.FAILURE_REASON_HAS_CHANGED_DURING_MINIMIZATION));
    }
  }

  public void testFailureReasonChangedExceptionTrace() {
    try {
      PropertyChecker.forAll(ourTestSettings, integers(), i -> {
        if (i == 0) {
          throw new AssertionError("fail");
        }
        else {
          throw new AssertionError("fail2");
        }
      });
      fail();
    }
    catch (PropertyFalsified e) {
      assertTrue(e.getMessage().contains(PropertyFalsified.FAILURE_REASON_HAS_CHANGED_DURING_MINIMIZATION));
    }
  }

  public void testExceptionWhileGeneratingValue() {
    try {
      PropertyChecker.forAll(ourTestSettings, from(data -> {
        throw new AssertionError("fail");
      }), i -> true);
      fail();
    }
    catch (GeneratorException ignore) {
    }
  }

  public void testExceptionWhileShrinkingValue() {
    try {
      PropertyChecker.forAll(ourTestSettings, listsOf(integers()).suchThat(l -> {
        if (l.size() == 1 && l.get(0) == 0) throw new RuntimeException("my exception");
        return true;
      }), l -> l.stream().allMatch(i -> i > 0));
      fail();
    }
    catch (PropertyFalsified e) {
      assertEquals("my exception", e.getFailure().getStoppingReason().getMessage());
      assertTrue(StatusNotifier.printStackTrace(e).contains("my exception"));
    }
  }

  public void testUnsatisfiableSuchThat() {
    try {
      PropertyChecker.forAll(integers(-1, 1).suchThat(i -> i > 2), i -> i == 0);
      fail();
    }
    catch (GeneratorException e) {
      assertTrue(e.getCause() instanceof CannotSatisfyCondition);
    }
  }

}
