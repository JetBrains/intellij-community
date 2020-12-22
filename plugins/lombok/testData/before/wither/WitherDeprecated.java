import lombok.experimental.Wither;

class WitherDeprecated {
	
	@Deprecated
	@Wither int annotation;
	
	/**
	 * @deprecated
	 */
	@Wither int javadoc;
	
	WitherDeprecated(int annotation, int javadoc) {
	}
}