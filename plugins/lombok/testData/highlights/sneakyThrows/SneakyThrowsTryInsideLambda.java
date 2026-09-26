import lombok.SneakyThrows;
import java.io.Reader;
import java.io.FileReader;

public class SneakyThrowsTryInsideLambda {
  @SneakyThrows
  public static void m() {
    Runnable r = () -> {
      try (Reader reader = new <error descr="Unhandled exception: java.io.FileNotFoundException">FileReader</error>("")) {}
    };
  }

  // everything is ok here
  @SneakyThrows
  void m2() {
    try (Reader reader = new FileReader("")) {}
  }

  @SneakyThrows
  public static void m3() {
    try {
      try (Reader reader = new FileReader("")) {
      }
    } catch (java.lang.NullPointerException e) {
    }
  }

  @SneakyThrows
  public static void m4() {
    class A {
      public void m() {
        try (Reader reader = new <error descr="Unhandled exception: java.io.FileNotFoundException">FileReader</error>("")) {
        }
      }
    }
  }
}