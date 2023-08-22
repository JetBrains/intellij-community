public class ToStringNewStyle {
	int b;
	double c;
	int f;
	int d;
	int f() {
		return 0;
	}
	int g;
	int h;
	int i;
	int j;
	@Override
	@SuppressWarnings("all")
	public String toString() {
		return "ToStringNewStyle(a=" + this.b + ", c=" + this.c + ", e=" + this.d + ", f=" + this.f() + ", g=" + this.g + ", i=" + this.i + ", h=" + this.h + ")";
	}
}
