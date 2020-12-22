@lombok.EqualsAndHashCode(of={})
class EqualsAndHashCode {
	int x;
	boolean[] y;
	Object[] z;
	String a;
	String b;
}

@lombok.EqualsAndHashCode(of="")
final class EqualsAndHashCode2 {
	int x;
	long y;
	float f;
	double d;
	boolean b;
}
