import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

@FieldDefaults(makeFinal = true)
class FieldDefaultsModifiers {
  int i1 = 0;
  @NonFinal int i2<caret> = 1;
}
