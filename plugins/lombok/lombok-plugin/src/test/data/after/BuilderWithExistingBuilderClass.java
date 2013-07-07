class BuilderWithExistingBuilderClass<T, K extends Number> {
	public static <Z extends Number> BuilderWithExistingBuilderClass<String, Z> staticMethod(Z arg1, boolean arg2, String arg3) {
		return null;
	}
	public static class BuilderWithExistingBuilderClassBuilder<Z extends Number> {
		private boolean arg2;
		private String arg3;
		private Z arg1;
		public void arg2(boolean arg) {
		}
		@java.lang.SuppressWarnings("all")
		BuilderWithExistingBuilderClassBuilder() {
		}
		@java.lang.SuppressWarnings("all")
		public BuilderWithExistingBuilderClassBuilder<Z> arg1(final Z arg1) {
			this.arg1 = arg1;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		public BuilderWithExistingBuilderClassBuilder<Z> arg3(final String arg3) {
			this.arg3 = arg3;
			return this;
		}
		@java.lang.SuppressWarnings("all")
		public BuilderWithExistingBuilderClass build() {
			return BuilderWithExistingBuilderClass.<Z>staticMethod(arg1, arg2, arg3);
		}
		@java.lang.Override
		@java.lang.SuppressWarnings("all")
		public java.lang.String toString() {
			return "BuilderWithExistingBuilderClass.BuilderWithExistingBuilderClassBuilder(arg1=" + this.arg1 + ", arg2=" + this.arg2 + ", arg3=" + this.arg3 + ")";
		}
	}
	@java.lang.SuppressWarnings("all")
	public static <Z extends Number> BuilderWithExistingBuilderClassBuilder<Z> builder() {
		return new BuilderWithExistingBuilderClassBuilder<Z>();
	}
}