package slowCheck;

import static slowCheck.Generator.integers;

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

}
