package de.plushnikov.builder.issue313;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ObjectApiResponse<K> {
  private K response;

	public static <Z> void create(Z res, ObjectApiResponseBuilder<Z> builder) {
//		ObjectApiResponseBuilder<Integer> response1 = ObjectApiResponse.<Integer>builder().response(Integer.valueOf(1));
//		ObjectApiResponseBuilder<Integer> response1 = ObjectApiResponse.builder().response(res);
		ObjectApiResponseBuilder<Z> response2 = builder.response(res);
	}

	public static class ObjectApiResponseBuilder<T> {
  }

}
