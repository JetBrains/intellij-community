import lombok.*;
import static lombok.AccessLevel.NONE;

@Data
@Getter(lombok.AccessLevel.NONE)
@Setter(lombok.AccessLevel.NONE)
class EqualsAndHashCodeWithSomeExistingMethods {
	int x;

	public int hashCode() {
		return 42;
	}
}

@Data
@Getter(lombok.AccessLevel.NONE)
@Setter(lombok.AccessLevel.NONE)
class EqualsAndHashCodeWithSomeExistingMethods2 {
	int x;

	protected boolean canEqual(Object other) {
		return false;
	}
}

@Data
@Getter(lombok.AccessLevel.NONE)
@Setter(lombok.AccessLevel.NONE)
class EqualsAndHashCodeWithAllExistingMethods {
	int x;

	public int hashCode() {
		return 42;
	}

	public boolean equals(Object other) {
		return false;
	}
}

@Data
@Getter(lombok.AccessLevel.NONE)
@Setter(lombok.AccessLevel.NONE)
class EqualsAndHashCodeWithNoExistingMethods {
	int x;
}

