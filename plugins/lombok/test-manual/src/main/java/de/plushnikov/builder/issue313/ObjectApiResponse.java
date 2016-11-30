package de.plushnikov.builder.issue313;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ObjectApiResponse<K> {
  private K response;


	public static <Z> void create(Z res, ObjectApiResponseBuilder<Z> builder) {
		ObjectApiResponseBuilder<Z> response1 = builder.response(res  );
	}

	public static class ObjectApiResponseBuilder<T> {

  }

}
