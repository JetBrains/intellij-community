import java.util.List;
import lombok.val;

public class ValRawType {
	public void test() {
		Element propElement = new Element();
		for (val attribute : propElement.attributes()) {
			val attr = (Attribute) attribute;
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