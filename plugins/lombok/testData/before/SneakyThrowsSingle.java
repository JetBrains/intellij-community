import java.io.IOException;

class SneakyThrowsSingle {
	@lombok.SneakyThrows(Throwable.class) 
	public void test() {
		System.out.println("test1");
	}
	
	@lombok.SneakyThrows(IOException.class)
	public void test2() {
		System.out.println("test2");
		throw new IOException();
	}
	
	@lombok.SneakyThrows(value=IOException.class)
	public void test3() {
		System.out.println("test3");
		throw new IOException();
	}

}