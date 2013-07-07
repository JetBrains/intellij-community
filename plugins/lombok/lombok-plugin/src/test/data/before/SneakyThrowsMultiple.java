import java.awt.AWTException;
import java.io.IOException;
import java.util.Random;

class SneakyThrowsMultiple {
	@lombok.SneakyThrows({IOException.class,Throwable.class})
	public void test() {
		System.out.println("test1");
		throw new IOException();
	}
	
	@lombok.SneakyThrows({AWTException.class,IOException.class})
	public void test2() {
		System.out.println("test2");
		if (new Random().nextBoolean()) {
			throw new IOException();
		}
		else {
			throw new AWTException("WHAT");
		}
	}
	
	@lombok.SneakyThrows(value={IOException.class,Throwable.class})
	public void test3() {
		System.out.println("test3");
		throw new IOException();
	}
}