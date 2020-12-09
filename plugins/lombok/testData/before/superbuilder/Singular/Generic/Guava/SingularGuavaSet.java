import com.google.common.collect.ImmutableSet;
import lombok.Singular;

@lombok.experimental.SuperBuilder
public class SingularGuavaSet<T> {
  @Singular
  private ImmutableSet rawTypes;
  @Singular
  private ImmutableSet<Integer> integers;
  @Singular
  private ImmutableSet<T> generics;
  @Singular
  private ImmutableSet<? extends Number> extendsGenerics;

  public static void main(String[] args) {
  }
}
