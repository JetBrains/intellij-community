@lombok.Builder
public class BuilderPredefined {
  private String name;
  private int age;

  public static class FirstInnerClassDefined {
    private boolean injectHere = false;
  }

  public static class BuilderPredefinedBuilder {
    private String name;

    public void age(int age) {
      this.age = age;
    }
  }
}
