import lombok.experimental.FieldDefaults;
import lombok.experimental.PackagePrivate;
import lombok.AccessLevel;

@FieldDefaults(level = AccessLevel.PUBLIC)
class FieldDefaultsModifiers {
  @PackagePrivate int i1<caret> = 0;
}
