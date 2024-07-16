abstract class Shape {
	public String color;
	public Shape() {
	}
	public void sColor(String c) {
		color = c;
	}
	public String gColor() {
		return color;
	}
	public abstract double area();
}