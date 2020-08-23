import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
class RedundantSlf4jDefinition {

    org.slf4j.Logger LOG2 = org.slf4j.LoggerFactory.getLogger(RedundantSlf4jDefinition.class);

  Logger LOG3 = LoggerFactory.getLogger(RedundantSlf4jDefinition.class);

  void foo() {
    log.info("foo() called");
  }

  public static void main(String[] args) {
    log.info("Using LOG1 Logger");
  }
}
