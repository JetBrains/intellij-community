import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

@FieldDefaults(level = AccessLevel.PRIVATE)
class FieldDefaultsModifiers {
  int i1<caret> = 0;
}
