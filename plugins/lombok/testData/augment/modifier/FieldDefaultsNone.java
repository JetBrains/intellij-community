import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

@FieldDefaults
class FieldDefaultsModifiers {
  int i1<caret> = 0;
}
