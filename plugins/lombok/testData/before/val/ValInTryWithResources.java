//version 7
import lombok.val;
import java.io.IOException;

public class ValInTryWithResources {
	public  void whyTryInsteadOfCleanup() throws IOException {
		try (val in = getClass().getResourceAsStream("ValInTryWithResources.class")) {
			val i = in;
			val j = in.read();
		}
	}
}