import lombok.Singular;

@lombok.Data
@lombok.Builder
public class ObjectApiResponse<K> {
  private K response;

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  ObjectApiResponse(K response) {
    this.response = response;
  }

  public static <Z> void create(Z res, ObjectApiResponseBuilder<Z> builder) {
    ObjectApiResponseBuilder<Z> response1 = builder.response(res);
  }

  public static class ObjectApiResponseBuilder<T> {
    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    private T response;

    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    public <T> ObjectApiResponseBuilder<T> response(T res) {
      this.response=response;
      return this;
    }

    @java.lang.SuppressWarnings("all")
    @javax.annotation.Generated("lombok")
    public ObjectApiResponse<T> build() {
      return new ObjectApiResponse<T>(response);
    }
  }
}
