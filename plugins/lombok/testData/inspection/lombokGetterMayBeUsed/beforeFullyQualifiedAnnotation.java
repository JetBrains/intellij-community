// "Use lombok @Getter for 'candidateField'" "true"

public class Getter {
  private int candidateField;
  private int fieldWithoutGetter;

  public int getCandidateField() {
    return candidateField<caret>;
  }

  public int completelyIrrevelantMethod() {
    return 0;
  }
}