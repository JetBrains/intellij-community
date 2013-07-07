import lombok.Getter;
class GetterDeprecated {
	
	@Deprecated
	@Getter int annotation;
	
	/**
	 * @deprecated
	 */
	@Getter int javadoc;
}