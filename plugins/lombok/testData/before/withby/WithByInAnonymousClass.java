import lombok.experimental.WithBy;

public class WithByInAnonymousClass {
	Object annonymous = new Object() {
    Inner createInner(String s) {
      return new Inner(s).withStringBy(s1 -> "sdsd");
    }

		@WithBy
		class Inner {
			private Inner(String string) { }

			private String string;
		}
	};
}