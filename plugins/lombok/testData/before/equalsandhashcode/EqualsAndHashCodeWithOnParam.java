@interface Nullable {
}

@lombok.EqualsAndHashCode(onParam=@__({@Nullable}))
class EqualsAndHashCodeWithOnParam {
	int x;
	boolean[] y;
	Object[] z;
	String a;
	String b;
}