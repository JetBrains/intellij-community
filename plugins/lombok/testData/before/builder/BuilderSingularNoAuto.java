//CONF: lombok.singular.auto = false
import java.util.List;

import lombok.Singular;

@lombok.Builder
class BuilderSingularNoAuto {
	@Singular private List<String> things;
	@Singular("widget") private List<String> widgets;
	@Singular private List<String> items;
}
