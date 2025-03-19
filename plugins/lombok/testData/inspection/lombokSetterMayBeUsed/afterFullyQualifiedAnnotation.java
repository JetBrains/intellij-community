// "Use lombok @Setter for 'candidateField'" "true"

public class Setter {
  @lombok.Setter
  private int candidateField;
  private int fieldWithoutSetter;

    public int completelyIrrevelantMethod() {
    return 0;
  }
}