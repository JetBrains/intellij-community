class SynchronizedNameNoSuchField {
	private Object read = new Object();
	private static Object READ = new Object();
	void test2() {
		System.out.println("two");
	}
}