import lombok.extern.java.Log;

@Log
class Outer {

    static class Inner extends Outer {
    static String log = "message";

    void test() {
      Outer.log.info("hello");
      Outer.log.info("hello");
    }
  }
}
