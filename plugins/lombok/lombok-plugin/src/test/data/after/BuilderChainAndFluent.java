class BuilderChainAndFluent {
	private final int yes;
	@SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	BuilderChainAndFluent(final int yes) {
		this.yes = yes;
	}
	@SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static class BuilderChainAndFluentBuilder {
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		private int yes;
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		BuilderChainAndFluentBuilder() {
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public void setYes(final int yes) {
			this.yes = yes;
		}
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public BuilderChainAndFluent build() {
			return new BuilderChainAndFluent(yes);
		}
		@Override
		@SuppressWarnings("all")
		@javax.annotation.Generated("lombok")
		public String toString() {
			return "BuilderChainAndFluent.BuilderChainAndFluentBuilder(yes=" + this.yes + ")";
		}
	}
	@SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public static BuilderChainAndFluentBuilder builder() {
		return new BuilderChainAndFluentBuilder();
	}
}