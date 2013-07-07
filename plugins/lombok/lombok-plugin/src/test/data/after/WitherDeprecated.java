class WitherDeprecated {
	@Deprecated
	int annotation;
	/**
	 * @deprecated
	 */
	int javadoc;
	WitherDeprecated(int annotation, int javadoc) {
	}
	@java.lang.Deprecated
	@java.lang.SuppressWarnings("all")
	public WitherDeprecated withAnnotation(final int annotation) {
		return this.annotation == annotation ? this : new WitherDeprecated(annotation, this.javadoc);
	}
	@java.lang.Deprecated
	@java.lang.SuppressWarnings("all")
	public WitherDeprecated withJavadoc(final int javadoc) {
		return this.javadoc == javadoc ? this : new WitherDeprecated(this.annotation, javadoc);
	}
}