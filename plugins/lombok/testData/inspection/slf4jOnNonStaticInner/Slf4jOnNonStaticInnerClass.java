import lombok.extern.slf4j.Slf4j;

@Slf4j
class Slf4jOnNonStaticInnerClassOuter {
  <error descr="'@Slf4j' is not supported on non-static nested classes.">@Slf4j</error>
  class Inner {
  }

  @Slf4j
  static class StaticInnerOk {
  }

  @Slf4j
  record SomeRecord() {

  }
}
