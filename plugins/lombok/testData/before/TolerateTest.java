import java.util.regex.Pattern;

@lombok.Setter
@lombok.Getter
class Tolerate {
	private Pattern pattern;
	
	@lombok.experimental.Tolerate public void setPattern(String pattern) {
		setPattern(Pattern.compile(pattern));
	}
}

@lombok.Getter
@lombok.experimental.Wither
@lombok.AllArgsConstructor
class Tolerate2 {
	private final Pattern pattern;
	
	@lombok.experimental.Tolerate public Tolerate2 withPattern(String pattern) {
		return withPattern(Pattern.compile(pattern));
	}

	public Tolerate2 withPattern(String nameGlob, String extensionGlob) {
		return withPattern(nameGlob.replace("*", ".*") + "\\." + extensionGlob.replace("*", ".*"));
	}
}
