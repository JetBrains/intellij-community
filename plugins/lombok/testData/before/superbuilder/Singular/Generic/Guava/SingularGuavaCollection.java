import com.google.common.collect.ImmutableCollection;
import lombok.Singular;

@lombok.experimental.SuperBuilder
public class SingularGuavaCollection<T> {
  @Singular
  private ImmutableCollection rawTypes;
  @Singular
  private ImmutableCollection<Integer> integers;
  @Singular
  private ImmutableCollection<T> generics;
  @Singular
  private ImmutableCollection<? extends Number> extendsGenerics;

  public static void main(String[] args) {
  }
}
