import java.util.logging.Level;
import lombok.extern.java.Log;
import lombok.Synchronized;

@Log
enum InjectField1 {
	A,
	B;

	private static final String LOG_MESSAGE = "static initializer";

	private String fieldA;

	static {
		log.log(Level.FINE, LOG_MESSAGE);
	}

	private String fieldB;

	@Synchronized
	void generateLockField() {
		System.out.println("lock field");
	}

	@Synchronized
	static void generateStaticLockField() {
		System.out.println("static lock field");
	}
}

@Log
class InjectField2 {
	private static final String LOG_MESSAGE = "static initializer";

	static {
		log.log(Level.FINE, LOG_MESSAGE);
	}

	@Synchronized
	void generateLockField() {
		System.out.println("lock field");
	}
}

@Log
class InjectField3 {
	static {
		log.log(Level.FINE, "static initializer");
	}
}