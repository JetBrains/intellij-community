// "Use lombok @Setter for 'candidateField'" "true"

public class Setter {
  private int candidateField;
  private int fieldWithoutSetter;

  public void setCandidateField(int param) {
    candidateField<caret> = param;
  }

  public int completelyIrrevelantMethod() {
    return 0;
  }
}