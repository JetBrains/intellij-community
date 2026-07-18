package foo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.slf4j.LoggerFactory.getLogger;

class RedundantSlf4jDefinitionInPackage {
  <warning descr="Slf4j Logger is defined explicitly. Use Lombok @Slf4j annotation instead.">private static final org.slf4j.Logger LOG1<caret> = org.slf4j.LoggerFactory.getLogger(RedundantSlf4jDefinitionInPackage.class);</warning>

  <warning descr="Slf4j Logger is defined explicitly. Use Lombok @Slf4j annotation instead.">org.slf4j.Logger LOG2 = org.slf4j.LoggerFactory.getLogger(RedundantSlf4jDefinitionInPackage.class);</warning>

  <warning descr="Slf4j Logger is defined explicitly. Use Lombok @Slf4j annotation instead.">Logger LOG3 = LoggerFactory.getLogger(RedundantSlf4jDefinitionInPackage.class);</warning>

  <warning descr="Slf4j Logger is defined explicitly. Use Lombok @Slf4j annotation instead.">Logger LOG4 = getLogger(RedundantSlf4jDefinitionInPackage.class);</warning>

  void foo() {
    LOG1.info("foo() called");
  }

  public static void main(String[] args) {
    LOG1.info("Using LOG1 Logger");
  }
}
