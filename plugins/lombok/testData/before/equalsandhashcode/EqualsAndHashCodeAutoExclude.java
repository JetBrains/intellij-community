@lombok.EqualsAndHashCode
class EqualsAndHashCodeAutoExclude {
	int x;
	String $a;
	transient String b;
}

@lombok.EqualsAndHashCode
class EqualsAndHashCodeAutoExclude2 {
	int x;
	@lombok.EqualsAndHashCode.Include
	String $a;
	@lombok.EqualsAndHashCode.Include
	transient String b;
}
