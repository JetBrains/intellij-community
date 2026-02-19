class ToStringAutoExclude {
	int x;
	String $a;
	transient String b;
	@Override
	@SuppressWarnings("all")
	public String toString() {
		return "ToStringAutoExclude(x=" + this.x + ", b=" + this.b + ")";
	}
}

class ToStringAutoExclude2 {
	int x;
	String $a;
	transient String b;
	@Override
	@SuppressWarnings("all")
	public String toString() {
		return "ToStringAutoExclude2(x=" + this.x + ", $a=" + this.$a + ", b=" + this.b + ")";
	}
}
