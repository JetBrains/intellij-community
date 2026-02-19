@lombok.experimental.UtilityClass
public class UtilityClassPlain {
  private final int CONSTANT = 5;

  public int addSomething(int in) {
    return in + CONSTANT;
  }

}