import lombok.Value;
import lombok.experimental.Wither;

@lombok.experimental.SuperBuilder
@Value
@Wither
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
      .<error descr="Cannot resolve method 'canNotSet' in 'BuilderDefaultValueBuilder'">canNotSet</error>(1);

    new BuilderDefaultValue(1,1);

    BuilderDefaultValue bdv = BuilderDefaultValue.builder()
      .mustSet(1)
      .canSet(1)
      .build();

    bdv.withCanSet(2).withMustSet(2);
  }
}
