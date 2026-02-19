import lombok.Singular;

import java.util.Map;
import java.util.SortedMap;

@lombok.experimental.SuperBuilder
public class SingularSortedMap<A, B> {
  @Singular
  private SortedMap rawTypes;
  @Singular
  private SortedMap<Integer, Float> integers;
  @Singular
  private SortedMap<A, B> generics;
  @Singular
  private SortedMap<? extends Number, ? extends String> extendsGenerics;

  public static void main(String[] args) {
  }
}
