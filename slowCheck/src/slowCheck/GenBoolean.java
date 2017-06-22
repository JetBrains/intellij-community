package slowCheck;

/**
 * @author peter
 */
public class GenBoolean {

  public static Generator<Boolean> bool() {
    return GenNumber.integers(0, 1).map(i -> i == 1);
  }

}
