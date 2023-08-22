import lombok.SneakyThrows;


public class SneakThrowsDoesntCatchCaughtException {

  @SneakyThrows
  public static void m() {
    final String file;
    try {
      file = "classpath:data/resource.json";
      new java.io.FileInputStream(file);
    } catch (java.io.IOException e) {
      System.out.println(<error descr="Variable 'file' might not have been initialized">file</error>);
    }
  }
}
