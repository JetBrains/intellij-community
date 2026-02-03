// version 8:
import java.util.function.Function;
import java.util.function.Consumer;

import lombok.experimental.ExtensionMethod;

@ExtensionMethod(ExtensionMethodFunctional.Extensions.class)
class ExtensionMethodFunctional {
	public void test() {
		String test = "test";
		test = test.map(s -> s.reverse());
		
		test.consume(s -> System.out.println("1: " + s), s -> System.out.println("2: " + s));
		test.consume(System.out::println, System.out::println);
	}
	
	static class Extensions {
		public static <T, R> R map(T value, Function<T, R> mapper) {
			return mapper.apply(value);
		}
		
		public static String reverse(String string) {
			return new StringBuilder(string).reverse().toString();
		}

		@SafeVarargs
		public static <T> void consume(T o, Consumer<T>... consumer) {
			for (int i = 0; i < consumer.length; i++) {
				consumer[i].accept(o);
			}
		}
	}
}
