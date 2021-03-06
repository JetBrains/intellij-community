import lombok.Synchronized;
class SynchronizedPlain1 {
	@lombok.Synchronized void test() {
		System.out.println("one");
	}
	@Synchronized void test2() {
		System.out.println("two");
	}
}
class SynchronizedPlain2 {
	@lombok.Synchronized static void test() {
		System.out.println("three");
	}
	@Synchronized static void test2() {
		System.out.println("four");
	}
}