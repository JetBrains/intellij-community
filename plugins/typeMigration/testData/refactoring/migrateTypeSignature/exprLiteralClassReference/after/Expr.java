import java.util.Set;

class Expr {
	private Class<Set> myField;
	public void meth() {
		myField = int.class;
		myField = int[].class;
		myField = Set.class;
		myField = Set[].class;
		myField = void.class;
	}
}
