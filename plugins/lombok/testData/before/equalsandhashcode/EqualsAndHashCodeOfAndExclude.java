@lombok.EqualsAndHashCode(of={"x"})
final class EqualsAndHashCodeOf {
	int x;
	int y;
}

@lombok.EqualsAndHashCode(exclude={"y"})
final class EqualsAndHashCodeExclude {
	int x;
	int y;
}
