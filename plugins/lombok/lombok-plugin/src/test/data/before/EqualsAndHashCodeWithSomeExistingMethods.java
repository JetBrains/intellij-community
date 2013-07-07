import lombok.*;
import static lombok.AccessLevel.NONE;

@Data
@Getter(NONE)
@Setter(NONE)
class EqualsAndHashCodeWithSomeExistingMethods {
	int x;
	
	public int hashCode() {
		return 42;
	}
}

@Data
@Getter(NONE)
@Setter(NONE)
class EqualsAndHashCodeWithSomeExistingMethods2 {
	int x;
	
	public boolean canEqual(Object other) {
		return false;
	}
}

@Data
@Getter(NONE)
@Setter(NONE)
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
@Getter(AccessLevel.NONE)
@Setter(lombok.AccessLevel.NONE)
class EqualsAndHashCodeWithNoExistingMethods {
	int x;
}

