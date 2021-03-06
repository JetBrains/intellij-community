import lombok.Singular;

@lombok.Data
@lombok.Builder
public class ObjectApiResponse<K> {
  private K response;

  public static <Z> void create(Z res, ObjectApiResponseBuilder<Z> builder) {
    ObjectApiResponseBuilder<Z> response1 = builder.response(res);
  }

  public static class ObjectApiResponseBuilder<T> {}
}
