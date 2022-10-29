class WithWithJavaBeansSpecCapitalization {
	int aField;
	WithWithJavaBeansSpecCapitalization(int aField) {
	}
	/**
	 * @return a clone of this object, except with this updated property (returns {@code this} if an identical value is passed).
	 */
	public WithWithJavaBeansSpecCapitalization withaField(final int aField) {
		return this.aField == aField ? this : new WithWithJavaBeansSpecCapitalization(aField);
	}
}
