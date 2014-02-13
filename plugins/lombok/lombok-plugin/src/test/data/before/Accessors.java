class AccessorsFluent {
	@lombok.Getter @lombok.Setter @lombok.experimental.Accessors(fluent=true)
	private String fieldName = "";
}

@lombok.experimental.Accessors(fluent=true)
@lombok.Getter
class AccessorsFluentOnClass {
	@lombok.Setter private String fieldName = "";
	@lombok.experimental.Accessors private String otherFieldWithOverride = "";
}

class AccessorsChain {
	@lombok.Setter @lombok.experimental.Accessors(chain=true) private boolean isRunning;
}

@lombok.experimental.Accessors(prefix="f")
class AccessorsPrefix {
	@lombok.Setter private String fieldName;
	@lombok.Setter private String fActualField;
}

@lombok.experimental.Accessors(prefix={"f", ""})
class AccessorsPrefix2 {
	@lombok.Setter private String fieldName;
	@lombok.Setter private String fActualField;
}

@lombok.experimental.Accessors(prefix="f")
@lombok.ToString
@lombok.EqualsAndHashCode
class AccessorsPrefix3 {
	private String fName;
	
	private String getName() {
		return fName;
	}
}

@lombok.experimental.Accessors(prefix={"_", "$"})
class AccessorsPrefix4 {
  @lombok.Setter private String _underscore;
  @lombok.Setter private String $DollarSign;
}

class AccessorsFluentGenerics<T extends Number> {
	@lombok.Setter @lombok.experimental.Accessors(fluent=true) private String name;
}

class AccessorsFluentNoChaining {
	@lombok.Setter @lombok.experimental.Accessors(fluent=true,chain=false) private String name;
}

class AccessorsFluentStatic<T extends Number> {
	@lombok.Setter @lombok.experimental.Accessors(fluent=true) private static String name;
}
