import lombok.Builder;
import lombok.Data;
import lombok.experimental.Tolerate;

import java.util.Optional;

@Data
@Builder(builderClassName = "Builder", setterPrefix = "with", toBuilder = true)
public class BuilderWithSetterPrefixAndExistedMethods {
  private final byte[] secret;
  private Optional<String> name;

  public static class Builder {
    public Builder withSecret(String value) {
      secret = value.getBytes();
      return this;
    }

    public Builder withSecret(byte[] value) {
      secret = value;
      return this;
    }

    @Tolerate
    public Builder withName(String name) {
      this.name = Optional.of(name);
      return this;
    }
  }

  public static void main(String[] args) {
    BuilderWithSetterPrefixAndExistedMethods obj = BuilderWithSetterPrefixAndExistedMethods.builder().withSecret("Secret").withName(Optional.of("aaa")).build();
    BuilderWithSetterPrefixAndExistedMethods rtn = obj.toBuilder().build();
    System.out.println(rtn);
  }
}
