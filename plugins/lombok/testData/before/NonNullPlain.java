import java.lang.annotation.*;

@lombok.RequiredArgsConstructor
@lombok.Getter
@lombok.Setter
class NonNullPlain {
	@lombok.NonNull
	int i;
	@lombok.NonNull
	String s;
	@NotNull
	Object o;
	
	@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
	@Retention(RetentionPolicy.CLASS)
	public @interface NotNull {}
}