@lombok.Builder
@lombok.Value
public class Zoo {
  private String meerkat;
  private String warthog;

  public Zoo create() {
    return new Zoo("tomon", "pumbaa");
  }
}