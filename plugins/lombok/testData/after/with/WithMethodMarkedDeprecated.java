class WithMethodMarkedDeprecated {
	@Deprecated
	int annotation;
	/**
	 * @deprecated
	 */
	int javadoc;
	WithMethodMarkedDeprecated(int annotation, int javadoc) {
	}
	@Deprecated
	@SuppressWarnings("all")
	public WithMethodMarkedDeprecated withAnnotation(final int annotation) {
		return this.annotation == annotation ? this : new WithMethodMarkedDeprecated(annotation, this.javadoc);
	}
	/**
	 * @deprecated
	 */
	@Deprecated
	@SuppressWarnings("all")
	public WithMethodMarkedDeprecated withJavadoc(final int javadoc) {
		return this.javadoc == javadoc ? this : new WithMethodMarkedDeprecated(this.annotation, javadoc);
	}
}
