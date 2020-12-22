class CleanupName {
	void test() {
		@lombok.Cleanup("toString") Object o = "Hello World!";
		System.out.println(o);
	}
	void test2() {
		@lombok.Cleanup(value="toString") Object o = "Hello World too!";
		System.out.println(o);
	}
}