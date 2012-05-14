abstract public class TestOverImpl40
  implements Runnable
{
  public String toString() {
    // overrides Object.toString()
    return super.toString();    //To change body of overridden methods use File | Settings | File Templates.
  }

  public void run() {
    // implements Runnable.run()
  }

  abstract void abstractMethod();   // is implemented

  void overrideableMethod() // is overridden
  {
    // do nothing
  }
}

class TestOverImpl2 extends TestOverImpl {
  void abstractMethod()    // implements
  {
    // implements abstractMethod()
  }

  void overrideableMethod()  // overrides
  {
    super.overrideableMethod();    //To change body of overridden methods use File | Settings | File Templates.
  }
}
