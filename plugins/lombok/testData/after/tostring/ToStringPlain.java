class ToString1 {
	int x;
	String name;
	@Override
	@SuppressWarnings("all")
	public String toString() {
		return "ToString1(x=" + this.x + ", name=" + this.name + ")";
	}
}

class ToString2 {
	int x;
	String name;
	@Override
	@SuppressWarnings("all")
	public String toString() {
		return "ToString2(x=" + this.x + ", name=" + this.name + ")";
	}
}
