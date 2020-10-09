import lombok.ToString;

@ToString
public class ToStringNewStyle {
	@ToString.Include(name = "a") int b;
	double c;
	int f;
	@ToString.Include(name = "e") int d;
	@ToString.Include int f() {
		return 0;
	}
	int g;
	@ToString.Include(rank = -1) int h;
	int i;
	@ToString.Exclude int j;
}
