import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class BuilderDefaultValue
{
  @lombok.Builder.Default
  int canSet = 0;
  int canNotSet = 0;
  int mustSet;

  public static void testMe()
  {
    BuilderDefaultValue.builder()
      .mustSet(1)
      .canSet(1);

    BuilderDefaultValue.builder()
      .<error descr="Cannot resolve method 'canNotSet(int)'">canNotSet</error>(1);

    new BuilderDefaultValue(1,1);
  }
}
