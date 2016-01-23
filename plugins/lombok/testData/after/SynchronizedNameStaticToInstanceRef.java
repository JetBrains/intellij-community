class SynchronizedNameStaticToInstanceRef {
	private Object read = new Object();
	private static Object READ = new Object();
	static void test3() {
		synchronized (SynchronizedNameStaticToInstanceRef.read) {
			System.out.println("three");
		}
	}
}