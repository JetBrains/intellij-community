//version 8: Jackson deps are at least Java7+.

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.Builder;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;

@Jacksonized
@Builder
public class JacksonBuilderSingular {
	@JsonAnySetter
	@Singular("any")
	private Map<String, Object> any;

	@JsonProperty("v_a_l_u_e_s")
	@Singular
	private List<String> values;

	@JsonAnySetter
	@Singular("guavaAny")
	private ImmutableMap<String, Object> guavaAny;

	@JsonProperty("guava_v_a_l_u_e_s")
	@Singular
	private ImmutableList<String> guavaValues;
}
