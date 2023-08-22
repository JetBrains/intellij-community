import lombok.NoArgsConstructor;
import lombok.NonNull;

//No validation error:" Class contains required fields, you have to force NoArgsConstructor"
@NoArgsConstructor
public class NoArgsConstructorWithNotNullFieldsShouldBeNotForced {

  @NonNull
  private String myVariable;
}