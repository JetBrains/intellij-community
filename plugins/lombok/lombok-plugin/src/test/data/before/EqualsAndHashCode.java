@lombok.EqualsAndHashCode
class EqualsAndHashCode {
	int x;
	boolean[] y;
	Object[] z;
	String a;
	String b;
}

@lombok.EqualsAndHashCode
final class EqualsAndHashCode2 {
	int x;
	long y;
	float f;
	double d;
}

@lombok.EqualsAndHashCode(callSuper=false)
final class EqualsAndHashCode3 extends EqualsAndHashCode {
}

@lombok.EqualsAndHashCode(callSuper=true)
class EqualsAndHashCode4 extends EqualsAndHashCode {
}