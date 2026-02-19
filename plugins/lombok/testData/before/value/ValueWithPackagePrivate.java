import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.PackagePrivate;

@Value
public class ValueWithPackagePrivate {
  private int privateInt;
  protected int protectedInt;
  public int publicInt;
  int anInt;

  @PackagePrivate
  int annotatedInt;

  @NonFinal
  int nonFinalInt;

  @PackagePrivate
  public int shouldBePublicInt;

  public static void main(String[] args) {
    final ValueWithPackagePrivate test = new ValueWithPackagePrivate(1, 2, 3, 4, 5, 6, 7);
    System.out.println(test);
  }
}
