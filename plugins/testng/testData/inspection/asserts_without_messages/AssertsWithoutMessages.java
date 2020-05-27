import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

class AssertsWithoutMessages {

  @Test
  public void bla() {
    <warning descr="'assertEquals()' without message">assertEquals</warning>("asdf", Integer.valueOf(1));
    <warning descr="'assertEquals()' without message">assertEquals</warning>(Double.valueOf(2.0), 1, 0.1);
    <warning descr="'assertEquals()' without message">assertEquals</warning>(new Object[1], new Object[2]);
    <warning descr="'assertFalse()' without message">assertFalse</warning>(false);

    AssertJUnit.<warning descr="'assertEquals()' without message">assertEquals</warning>(new Object[1], new Object[2]);
    int i = 2;
    AssertJUnit.assertEquals("asdf", 0, i);
    AssertJUnit.fail("hello!");

    fail("bla", new RuntimeException());
    <warning descr="'fail()' without message">fail</warning>();
  }

}