import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum EnumClass {
  ENUM1, ENUM2
}

class SomeOtherClass {
  public static void main(String[] args) {
    System.out.println(EnumClass.ENUM1.name());
  }
}
