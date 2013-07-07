class GetterDeprecated {
	@Deprecated
	int annotation;
	/**
	 * @deprecated
	 */
	int javadoc;
	@java.lang.Deprecated
	@java.lang.SuppressWarnings("all")
	public int getAnnotation() {
		return this.annotation;
	}
	@java.lang.Deprecated
	@java.lang.SuppressWarnings("all")
	public int getJavadoc() {
		return this.javadoc;
	}
}