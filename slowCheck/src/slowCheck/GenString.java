package slowCheck;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author peter
 */
public class GenString {
  public static Generator<String> stringOf(@NotNull String possibleChars) {
    List<Character> chars = IntStream.range(0, possibleChars.length()).mapToObj(possibleChars::charAt).collect(Collectors.toList());
    return stringOf(Generator.anyValue(chars));
  }

  public static Generator<String> stringOf(@NotNull Generator<Character> charGen) {
    return GenCollection.listOf(charGen).map(chars -> {
      StringBuilder sb = new StringBuilder();
      chars.forEach(sb::append);
      return sb.toString();
    });
  }

  public static Generator<String> asciiIdentifier() {
    return stringOf(Generator.frequency(50, GenChar.asciiLetter(), 
                                        5, GenChar.digit(),
                                        1, Generator.constant('_')))
      .suchThat(s -> s.length() > 0 && !Character.isDigit(s.charAt(0)));
  }
}
