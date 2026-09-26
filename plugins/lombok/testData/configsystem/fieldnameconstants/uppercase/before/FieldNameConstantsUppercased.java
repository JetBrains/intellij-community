//CONF: lombok.fieldNameConstants.uppercase = true
import lombok.AccessLevel;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(level = AccessLevel.PACKAGE)
public class FieldNameConstantsUppercased {
  String iAmADvdPlayer;
  int $skipMe;
  static double skipMeToo;
  @FieldNameConstants.Exclude
  int andMe;
  String butPrintMePlease;
}
