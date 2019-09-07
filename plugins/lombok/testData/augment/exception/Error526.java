package augment.exception;

import lombok.Value;
import lombok.experimental.Accessors;

@Accessors
@Value(staticConstructor = "of")
public final class Error526<C, T> {
  <caret>
  private final C code;
  private final T subject;
  private final String reason;

}
