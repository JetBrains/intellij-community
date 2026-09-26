import com.google.common.collect.ImmutableTable;
import lombok.Singular;

import java.util.Map;

@lombok.Builder
public class SingularGuavaTable<A, B, C> {
  @Singular
  private ImmutableTable rawTypes;
  @Singular
  private ImmutableTable<Integer, Float, String> integers;
  @Singular
  private ImmutableTable<A, B, C> generics;
  @Singular
  private ImmutableTable<? extends Number, ? extends Float, ? extends String> extendsGenerics;

  public static void main(String[] args) {
  }
}
