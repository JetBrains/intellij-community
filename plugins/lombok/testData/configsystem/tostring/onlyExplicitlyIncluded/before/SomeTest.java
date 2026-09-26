import lombok.ToString;

@ToString
public class SomeTest {

  int x;

  @lombok.ToString.Include
  int y;

  String z;
}
