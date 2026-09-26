import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

@FieldDefaults(level = AccessLevel.PROTECTED)
class FieldDefaultsModifiers {
  int i1<caret> = 0;
}
