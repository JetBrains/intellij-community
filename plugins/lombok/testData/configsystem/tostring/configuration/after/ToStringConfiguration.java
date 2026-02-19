class ToStringConfiguration {
	int x;
//	@Override
//	@SuppressWarnings("all")
	public String toString() {
		return "ToStringConfiguration(" + this.x + ")";
	}
//	@SuppressWarnings("all")
	public int getX() {
		return this.x;
	}
}

class ToStringConfiguration2 {
	int x;
//	@Override
//	@SuppressWarnings("all")
	public String toString() {
		return "ToStringConfiguration2(x=" + this.x + ")";
	}
}

class ToStringConfiguration3 {
	int x;
//	@Override
//	@SuppressWarnings("all")
	public String toString() {
		return "ToStringConfiguration3(" + this.getX() + ")";
	}
//	@SuppressWarnings("all")
	public int getX() {
		return this.x;
	}
}
