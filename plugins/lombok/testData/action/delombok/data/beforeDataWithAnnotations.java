import lombok.Data;
import lombok.NonNull;

@Data
public class DataWithAnnotations {
  <caret>
  @NonNull
  @Deprecated
  @SuppressWarnings("any")
  private Integer someParentInteger;
}
