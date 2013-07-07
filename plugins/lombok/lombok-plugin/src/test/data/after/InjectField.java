import java.util.logging.Level;

enum InjectField1 {
	A,
	B;

	private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(InjectField1.class.getName());
	@java.lang.SuppressWarnings("all")
	private final java.lang.Object $lock = new java.lang.Object[0];
	@java.lang.SuppressWarnings("all")
	private static final java.lang.Object $LOCK = new java.lang.Object[0];

	private static final String LOG_MESSAGE = "static initializer";

	private String fieldA;

	static {
		log.log(Level.FINE, LOG_MESSAGE);
	}

	private String fieldB;

	void generateLockField() {
		synchronized (this.$lock) {
			System.out.println("lock field");
		}
	}

	static void generateStaticLockField() {
		synchronized (InjectField1.$LOCK) {
			System.out.println("static lock field");
		}
	}
}

class InjectField2 {
	private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(InjectField2.class.getName());
	@java.lang.SuppressWarnings("all")
	private final java.lang.Object $lock = new java.lang.Object[0];

	private static final String LOG_MESSAGE = "static initializer";

	static {
		log.log(Level.FINE, LOG_MESSAGE);
	}

	void generateLockField() {
		synchronized (this.$lock) {
			System.out.println("lock field");
		}
	}
}

class InjectField3 {
	private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(InjectField3.class.getName());
	static {
		log.log(Level.FINE, "static initializer");
	}
}