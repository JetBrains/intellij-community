import lombok.Setter;
class SetterDeprecated {
	
	@Deprecated
	@Setter int annotation;
	
	/**
	 * @deprecated
	 */
	@Setter int javadoc;
}