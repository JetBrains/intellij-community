package de.plushnikov.builder;

@lombok.Builder
public class BuilderConstructorException {

  private int i;

  //TODO fix it
  public BuilderConstructorException(int i) //throws Exception
  {
    System.out.println("sss");
  }

  public static void main(String[] args) {
    try {
      builder().i(2).build();
    } catch (Exception ignore) {
    }
  }
}
