import com.google.common.collect.ImmutableSortedSet;
import lombok.Singular;

@lombok.experimental.SuperBuilder
public class SingularGuavaSortedSet<T> {
  @Singular
  private ImmutableSortedSet rawTypes;
  @Singular
  private ImmutableSortedSet<Integer> integers;
  @Singular
  private ImmutableSortedSet<T> generics;
  @Singular
  private ImmutableSortedSet<? extends Number> extendsGenerics;

  public static void main(String[] args) {
  }
}
