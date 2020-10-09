import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

@FieldDefaults(level = AccessLevel.PUBLIC)
class FieldDefaultsModifiers {
  protected int i1<caret> = 0;
}
