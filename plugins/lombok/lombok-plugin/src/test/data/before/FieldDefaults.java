@lombok.experimental.FieldDefaults(makeFinal = true)
class FieldDefaults1 {
	int x;
	@lombok.experimental.NonFinal int y;
	
	FieldDefaults1(int x) {
		this.x = x;
	}
}

@lombok.experimental.FieldDefaults(level = lombok.AccessLevel.PRIVATE)
class FieldDefaults2 {
	@lombok.experimental.PackagePrivate int x;
	int y;
}