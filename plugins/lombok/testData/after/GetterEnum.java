enum GetterEnum {
	ONE(1, "One");
	private final int id;
	private final String name;
	@java.lang.SuppressWarnings("all")
	private GetterEnum(final int id, final String name) {
		this.id = id;
		this.name = name;
	}
	@java.lang.SuppressWarnings("all")
	public int getId() {
		return this.id;
	}
	@java.lang.SuppressWarnings("all")
	public String getName() {
		return this.name;
	}
}