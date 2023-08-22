import lombok.Value;

@Value
public class Foo {
  String one;
  String two = "foo";

  public static void main(String[] args) {
    System.out.println(new Foo("one"));
  }
}