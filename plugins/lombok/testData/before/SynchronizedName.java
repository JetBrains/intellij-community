class SynchronizedName {
	private Object read = new Object();
	private static Object READ = new Object();
	
	@lombok.Synchronized("read") void test1() {
		System.out.println("one");
	}
	@lombok.Synchronized("READ") void test4() {
		System.out.println("four");
	}	
	@lombok.Synchronized(value="read") void test5() {
		System.out.println("five");
	}
}
