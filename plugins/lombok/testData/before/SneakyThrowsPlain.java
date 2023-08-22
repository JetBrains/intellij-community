import lombok.SneakyThrows;
class SneakyThrowsPlain {
	@lombok.SneakyThrows SneakyThrowsPlain() {
		super();
		System.out.println("constructor");
	}
	
	@lombok.SneakyThrows SneakyThrowsPlain(int x) {
		this();
		System.out.println("constructor2");
	}
	
	@lombok.SneakyThrows public void test() {
		System.out.println("test1");
	}
	
	@SneakyThrows public void test2() {
		System.out.println("test2");
	}
}