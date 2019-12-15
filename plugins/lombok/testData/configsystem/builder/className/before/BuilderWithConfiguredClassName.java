package before.builder.ClassName;

import lombok.Builder;

@Builder
public class BuilderWithConfiguredClassName {

  private final String field;

  public static final class Builder {

    public Builder copy() {
      return builder().field(field);
    }

  }

}
