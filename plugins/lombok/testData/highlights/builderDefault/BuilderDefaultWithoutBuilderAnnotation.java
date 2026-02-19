import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
// @Builder <-- this is missing
public class BuilderDefaultWithoutBuilderAnnotation {

  private String key;
  private String description;

  <error descr="@Builder.Default requires @Builder or @SuperBuilder on the class for it to mean anything.">@Builder.Default</error>
  private Date created = new Date();
}