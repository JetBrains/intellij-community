public enum ConstructorEnum {

	A(1), B(2);

	private final int x;

	public int getX() {
		return this.x;
	}

	ConstructorEnum(int x) {
		this.x = x;
	}
}