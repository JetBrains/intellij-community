import lombok.experimental.FieldDefaults;

@FieldDefaults(makeFinal = true)
class FieldDefaultsModifiers {
  int i1<caret> = 0;
}
