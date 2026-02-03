@lombok.ToString
class ToStringAutoExclude {
	int x;
	String $a;
	transient String b;
}

@lombok.ToString
class ToStringAutoExclude2 {
	int x;
	@lombok.ToString.Include
	String $a;
	transient String b;
}
