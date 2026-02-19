import lombok.Singular;

import java.util.Map;
import java.util.NavigableMap;

@lombok.Builder
public class SingularNavigableMap<A, B> {
  @Singular
  private NavigableMap rawTypes;
  @Singular
  private NavigableMap <Integer, Float> integers;
  @Singular
  private NavigableMap <A, B> generics;
  @Singular
  private NavigableMap <? extends Number, ? extends String> extendsGenerics;

  public static void main(String[] args) {
  }
}
