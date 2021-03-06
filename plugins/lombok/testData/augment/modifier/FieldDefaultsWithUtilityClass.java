import lombok.experimental.UtilityClass;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

@UtilityClass
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FieldDefaultsWithUtilityClass {
  private boolean myStaticField<caret>;
}
