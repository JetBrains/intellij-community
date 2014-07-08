class Expr {
	private class Ancestor {}
	private class Subject extends Ancestor {}
	private class Descendant extends Subject {}

	private Ancestor myField;

	public void meth() {
		myField = new Ancestor();
		myField = this.new Ancestor();
		myField = new Ancestor() {};

		myField = new Subject();
		myField = this.new Subject();
		myField = new Subject() {};

		myField = new Descendant();
		myField = this.new Descendant();
		myField = new Descendant() {};
	}
}
