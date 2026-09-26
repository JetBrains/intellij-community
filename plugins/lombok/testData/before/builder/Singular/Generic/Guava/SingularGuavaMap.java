import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.Singular;

@lombok.Builder
public class SingularGuavaMap<A, B> {
  @Singular
  private ImmutableMap rawTypes;
  @Singular
  private ImmutableMap<Integer, Float> integers;
  @Singular
  private ImmutableMap<A,B> generics;
  @Singular
  private ImmutableMap<? extends Number, ? extends String> extendsGenerics;

  public static void main(String[] args) {
  }
}
