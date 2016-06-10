class BuilderChainAndFluent {
	private final int yes;
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	BuilderChainAndFluent(final int yes) {
		this.yes = yes;
	}
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static class BuilderChainAndFluentBuilder {
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private int yes;
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		BuilderChainAndFluentBuilder() {
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public void setYes(final int yes) {
			this.yes = yes;
		}
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderChainAndFluent build() {
			return new BuilderChainAndFluent(yes);
		}
		@java.lang.Override
		@java.lang.SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public java.lang.String toString() {
			return "BuilderChainAndFluent.BuilderChainAndFluentBuilder(yes=" + this.yes + ")";
		}
	}
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static BuilderChainAndFluentBuilder builder() {
		return new BuilderChainAndFluentBuilder();
	}
}