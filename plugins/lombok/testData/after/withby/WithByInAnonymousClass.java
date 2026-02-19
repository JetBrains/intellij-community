public class WithByInAnonymousClass {
	Object annonymous = new Object() {
    Inner createInner(String s) {
      return new Inner(s).withStringBy(s1 -> "sdsd");
    }

		class Inner {
			private Inner(String string) {
			}
			private String string;
			@java.lang.SuppressWarnings("all")
			@lombok.Generated
			public Inner withStringBy(final java.util.function.Function<? super String, ? extends String> transformer) {
				return new Inner(transformer.apply(this.string));
			}
		}
	};
}
