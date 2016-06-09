import lombok.experimental.FieldDefaults;

@FieldDefaults(makeFinal = false)
class FieldDefaultsModifiers {
  int i1<caret> = 0;
}
