import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

public class EduTestRunner {
  public static void main(String[] args) throws ClassNotFoundException {
    Class<?> testClass = EduTestRunner.class.getClassLoader().loadClass(args[0]);
    JUnitCore runner = new JUnitCore();
    runner.addListener(new RunListener() {
      @Override
      public void testFailure(Failure failure) throws Exception {
        System.out.println("#educational_plugin FAILED + " + failure.getMessage());
      }
    });
    runner.run(testClass);
  }
}