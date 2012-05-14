public class RearrangementTest28
  implements InterfaceName, InterfaceName2
{
  // start of fields
  int i;
// end of fields

  // start of interface InterfaceName
  public int getTime() {
    return getAnother();
  }

  // end of interface InterfaceName
// start of interface InterfaceName2
  public int getDate() {
    return 0;
  }

  // end of interface InterfaceName2
  public int getAnother() {
    return 0;
  }

  // Level 1 methods
  public void m1() {
    m2();
    m3();
  }

  // Level 2 methods
  public void m2() {
  }

  public void m3() {
  }
// end Level 2 methods
// end Level 1 methods
}

interface InterfaceName {
  public int getTime();
}

interface InterfaceName2 {
  public int getDate();
}
