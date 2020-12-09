import lombok.Delegate;

abstract class DelegateOnMethods {

	@Delegate
	public abstract Bar getBar();

	public static interface Bar {
		void bar(java.util.ArrayList<java.lang.String> list);
	}
}