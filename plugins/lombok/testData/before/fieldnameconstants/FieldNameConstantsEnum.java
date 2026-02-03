import lombok.experimental.FieldNameConstants;
import lombok.AccessLevel;

@FieldNameConstants(onlyExplicitlyIncluded = true, asEnum = true, innerTypeName = "TypeTest")
public class FieldNameConstantsEnum {
  @FieldNameConstants.Include
  String iAmADvdPlayer;
  @FieldNameConstants.Include
  int $dontSkipMe;
  @FieldNameConstants.Include
  static double alsoDontSkipMe;
  int butSkipMe;
}
