import lombok.experimental.FieldNameConstants;
import lombok.AccessLevel;

@FieldNameConstants(level = AccessLevel.PACKAGE)
public class FieldNameConstantsBasic {
  String iAmADvdPlayer;
  int $skipMe;
  static double skipMeToo;
  @FieldNameConstants.Exclude int andMe;
  String butPrintMePlease;
}
