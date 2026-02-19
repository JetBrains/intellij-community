import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class EqualsAndHashCodeNewStyle {
	@EqualsAndHashCode.Include int b;
	double c;
	int f;
	@EqualsAndHashCode.Include int d;
	@EqualsAndHashCode.Include int f() {
		return 0;
	}
	int g;
	@EqualsAndHashCode.Include(replaces = "g") long i() {
		return 0;
	}
	@EqualsAndHashCode.Exclude int j;
}
