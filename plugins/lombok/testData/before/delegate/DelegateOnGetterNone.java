import lombok.AccessLevel;
import lombok.Delegate;
import lombok.Getter;

@Getter
class DelegateOnGetterNone {

	@Delegate @Getter(AccessLevel.NONE) private final Bar bar = null;

	private interface Bar {
		void setList(java.util.ArrayList<java.lang.String> list);
		int getInt();
	}
}
