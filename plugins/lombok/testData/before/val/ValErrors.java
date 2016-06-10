import lombok.val;

public class ValErrors {
	public void unresolvableExpression() {
		val c = d;
	}
	
	public void arrayInitializer() {
		val e = { "foo", "bar"};
	}
}