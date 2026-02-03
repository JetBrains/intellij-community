//CONF: lombok.ToString.includeFieldNames = false
//CONF: lombok.ToString.doNotUseGetters = true

import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
class ToStringConfiguration {
	int x;
}

@ToString(includeFieldNames=true) class ToStringConfiguration2 {
	int x;
}

@ToString(doNotUseGetters=false) @Getter
class ToStringConfiguration3 {
	int x;
}
