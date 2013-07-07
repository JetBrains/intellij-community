class SynchronizedPlain1 {
	@java.lang.SuppressWarnings("all")
	private final java.lang.Object $lock = new java.lang.Object[0];
	void test() {
		synchronized (this.$lock) {
			System.out.println("one");
		}
	}
	void test2() {
		synchronized (this.$lock) {
			System.out.println("two");
		}
	}
}
class SynchronizedPlain2 {
	@java.lang.SuppressWarnings("all")
	private static final java.lang.Object $LOCK = new java.lang.Object[0];
	static void test() {
		synchronized (SynchronizedPlain2.$LOCK) {
			System.out.println("three");
		}
	}
	static void test2() {
		synchronized (SynchronizedPlain2.$LOCK) {
			System.out.println("four");
		}
	}
}