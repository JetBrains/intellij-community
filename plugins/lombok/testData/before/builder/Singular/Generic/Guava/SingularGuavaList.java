import com.google.common.collect.ImmutableList;
import lombok.Singular;

@lombok.Builder
public class SingularGuavaList<T> {
  @Singular
  private ImmutableList rawTypes;
  @Singular
  private ImmutableList<Integer> integers;
  @Singular
  private ImmutableList<T> generics;
  @Singular
  private ImmutableList<? extends Number> extendsGenerics;

  public static void main(String[] args) {
  }
}
