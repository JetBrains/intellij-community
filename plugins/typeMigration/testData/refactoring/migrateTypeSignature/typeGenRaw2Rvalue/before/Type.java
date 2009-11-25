import java.util.Set;

class Any {}

class Type {
	private Set<Any> myField;
	public void meth(Set<Any> p) {
		myField = p;
	}
}
