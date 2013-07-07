class GetterWithDollar1 {
	int $i;
	@java.lang.SuppressWarnings("all")
	public int get$i() {
		return this.$i;
	}
}
class GetterWithDollar2 {
	int $i;
	int i;
	@java.lang.SuppressWarnings("all")
	public int get$i() {
		return this.$i;
	}
	@java.lang.SuppressWarnings("all")
	public int getI() {
		return this.i;
	}
}