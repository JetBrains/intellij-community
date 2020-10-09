import lombok.Singular;

import java.util.Map;

@lombok.Builder
public class SingularMap<A, B> {
  @Singular
  private Map rawTypes;
  @Singular
  private Map<Integer, Float> integers;
  @Singular
  private Map<A, B> generics;
  @Singular
  private Map<? extends Number, ? extends String> extendsGenerics;

  public static void main(String[] args) {
  }
}
