// "Use lombok @Getter for 'candidateField'" "true"

public class Getter {
  @lombok.Getter
  private int candidateField;
  private int fieldWithoutGetter;

    public int completelyIrrevelantMethod() {
    return 0;
  }
}