//CONF: lombok.copyableAnnotations += TA

import lombok.With;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@interface TA {
}

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@interface TB {
}

class WithWithTypeAnnos {
	@With
  final @TA @TB List<String> foo;

	WithWithTypeAnnos(@TA @TB List<String> foo) {
		this.foo = foo;
	}
}
