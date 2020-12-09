import java.util.List;

public class ValRawType {

	public void test() {
		Element propElement = new Element();
		for (final java.lang.Object attribute : propElement.attributes()) {
			final ValRawType.Attribute attr = (Attribute)attribute;
		}
	}

	static class Element {
		public List attributes() {
			return null;
		}
	}

	static class Attribute {
	}
}