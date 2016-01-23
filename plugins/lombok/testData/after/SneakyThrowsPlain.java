class SneakyThrowsPlain {
	SneakyThrowsPlain() {
		try {
			System.out.println("constructor");
		} catch (final java.lang.Throwable $ex) {
			throw lombok.Lombok.sneakyThrow($ex);
		}
	}
	SneakyThrowsPlain(int x) {
		this();
		try {
			System.out.println("constructor2");
		} catch (final java.lang.Throwable $ex) {
			throw lombok.Lombok.sneakyThrow($ex);
		}
	}
	public void test() {
		try {
			System.out.println("test1");
		} catch (final java.lang.Throwable $ex) {
			throw lombok.Lombok.sneakyThrow($ex);
		}
	}
	public void test2() {
		try {
			System.out.println("test2");
		} catch (final java.lang.Throwable $ex) {
			throw lombok.Lombok.sneakyThrow($ex);
		}
	}
}