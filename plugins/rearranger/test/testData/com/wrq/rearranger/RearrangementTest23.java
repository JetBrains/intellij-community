public class RearrangementTest23
  implements IFace2, IFace1
{
  int method1() {
    return 4;
  }

  public boolean getB() {
    return false;
  }

  public int getY() {
    return 6;
  }

  public int getX() {
    return getA() ? 5 : 3;
  }

  public boolean getA() {
    return true;
  }

  int method2() {
    return 7;
  }
}

interface IFace2 {
  public boolean getA();

  public boolean getB();
}

interface IFace1 {
  public int getY();

  public int getX();
}
