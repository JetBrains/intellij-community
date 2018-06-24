import lombok.experimental.FieldNameConstants;
import lombok.AccessLevel;

@FieldNameConstants
public class FieldNameConstantsBasic {
	@FieldNameConstants(level = AccessLevel.PROTECTED)
	String iAmADvdPlayer;
	int $skipMe;
	static double skipMeToo;
	String butPrintMePlease;
}
