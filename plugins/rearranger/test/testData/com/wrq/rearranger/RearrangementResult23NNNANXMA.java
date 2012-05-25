public class RearrangementTest23
  implements IFace2, IFace1
{
/**** Interface IFace2 Header ****/

  public boolean getA() {
    return true;
  }

  public boolean getB() {
    return false;
  }
/**** Interface IFace2 Trailer ***/
/**** Interface IFace1 Header ****/

  public int getX() {
    return getA() ? 5 : 3;
  }

  public int getY() {
    return 6;
  }
/**** Interface IFace1 Trailer ***/
  int method1() {
    return 4;
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
