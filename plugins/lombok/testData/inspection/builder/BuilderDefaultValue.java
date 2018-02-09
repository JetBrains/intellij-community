import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class BuilderDefaultValue
{
  @Builder.Default
  int canSet = 0;
  int canNotSet = 0;

  public static void testMe()
  {
    BuilderDefaultValue.builder()
      .canSet(1);

    BuilderDefaultValue.builder()
      .<error descr="Cannot resolve method 'canNotSet(int)'">canNotSet</error>(1);
  }
}
