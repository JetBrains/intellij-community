import com.google.common.collect.ImmutableBiMap;
import lombok.Singular;

@lombok.Builder
public class SingularGuavaBiMap<A, B> {
  @Singular
  private ImmutableBiMap rawTypes;
  @Singular
  private ImmutableBiMap<Integer, Float> integers;
  @Singular
  private ImmutableBiMap<A, B> generics;
  @Singular
  private ImmutableBiMap<? extends Number, ? extends String> extendsGenerics;

  public static void main(String[] args) {
  }

}
