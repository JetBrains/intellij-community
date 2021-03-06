
public class ObjectApiResponse<K> {
  private K response;

  @java.lang.SuppressWarnings("all")
  ObjectApiResponse(K response) {
    this.response = response;
  }

  public static <Z> void create(Z res, ObjectApiResponseBuilder<Z> builder) {
    ObjectApiResponseBuilder<Z> response1 = builder.response(res);
  }

  public static class ObjectApiResponseBuilder<T> {
    @java.lang.SuppressWarnings("all")
    private T response;

    ObjectApiResponseBuilder() {
    }

    @java.lang.SuppressWarnings("all")
    public ObjectApiResponseBuilder<T> response(T response) {
      this.response=response;
      return this;
    }

    @java.lang.SuppressWarnings("all")
    public ObjectApiResponse<T> build() {
      return new ObjectApiResponse<T>(response);
    }

    public String toString() {
      return "ObjectApiResponse.ObjectApiResponseBuilder(response=" + this.response + ")";
    }
  }

  public static <T> ObjectApiResponseBuilder<T> builder() {
    return new ObjectApiResponseBuilder<T>();
  }

  public K getResponse() {
    return this.response;
  }

  public void setResponse(K response) {
    this.response = response;
  }

  public boolean equals(Object o) {
    if (o == this)
      return true;

    if (!(o instanceof ObjectApiResponse))
      return false;

    final ObjectApiResponse<?> other = (ObjectApiResponse<?>) o;
    if (!other.canEqual((Object) this))
      return false;

    final Object this$response = this.getResponse();
    final Object other$response = other.getResponse();
    if (this$response == null ? other$response != null : !this$response.equals(other$response))
      return false;

    return true;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $response = this.getResponse();
    result = result * PRIME + ($response == null ? 43 : $response.hashCode());
    return result;
  }

  protected boolean canEqual(Object other) {
    return other instanceof ObjectApiResponse;
  }

  public String toString() {
    return "ObjectApiResponse(response=" + this.getResponse() + ")";
  }
}
