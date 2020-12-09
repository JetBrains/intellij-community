import com.google.common.collect.ImmutableSortedMap;
import lombok.Singular;

@lombok.experimental.SuperBuilder
public class SingularGuavaSortedMap<A, B> {
  @Singular
  private ImmutableSortedMap rawTypes;
  @Singular
  private ImmutableSortedMap<Integer, Float> integers;
  @Singular
  private ImmutableSortedMap<A, B> generics;
  @Singular
  private ImmutableSortedMap<? extends Number, ? extends String> extendsGenerics;

  public static void main(String[] args) {
  }
}
