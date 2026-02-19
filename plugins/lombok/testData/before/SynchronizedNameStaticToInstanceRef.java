class SynchronizedNameStaticToInstanceRef {
	private Object read = new Object();
	private static Object READ = new Object();
	
	@lombok.Synchronized("read") static void test3() {
		System.out.println("three");
	}	
}
