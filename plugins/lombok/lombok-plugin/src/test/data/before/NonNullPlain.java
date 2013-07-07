import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@lombok.RequiredArgsConstructor
@lombok.Getter
@lombok.Setter
class NonNullPlain {
  @lombok.NonNull
  int i;
  @lombok.NonNull
  String s;
  @NotNull
  Object o;

  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
  @Retention(RetentionPolicy.CLASS)
  public @interface NotNull {
  }
}