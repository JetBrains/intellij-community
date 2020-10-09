import lombok.experimental.Delegate;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class DelegateGenericInterfaceIssue88 {
  interface Visitor88 {
    <T> T visit(T object);
  }

  private static class Visitor88Impl implements Visitor88 {
    @Override
    public <T> T visit(T object) {
      System.out.println("Lets see what we got: " + object.getClass());
      return object;
    }
  }

  private static class MyWorker88 {
    String doWork() {
      return "The Work Was done";
    }
  }

  @Delegate
  private Visitor88 visitor = new Visitor88Impl();

  private MyWorker88 myWorker;

  @Before
  public void setUp() throws Exception {
    myWorker = new MyWorker88();
  }

  @Test
  public void testDoWorkWithDelegation() throws Exception {
    String work = visit(myWorker).doWork();
    assertNotNull(work);
    System.out.println("testDoWorkWithDelegation - Work:" + work);
  }

  @Test
  public void testDoWorkWithoutDelegation() throws Exception {
    String work = visitor.visit(myWorker).doWork();
    assertNotNull(work);
    System.out.println("testDoWorkWithoutDelegation - Work: " + work);
  }
}
