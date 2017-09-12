class WitherAndAccessors {

	final int x = 10;

	int y = 20;

	@lombok.experimental.Accessors(fluent=true)
	final int z;

	@java.beans.ConstructorProperties({"y", "z"})
	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public WitherAndAccessors(final int y, final int z) {
		this.y = y;
		this.z = z;
	}

	@java.lang.SuppressWarnings("all")
	@javax.annotation.Generated("lombok")
	public WitherAndAccessors withZ(final int z) {
		return this.z == z ? this : new WitherAndAccessors(this.y, z);
	}
}
