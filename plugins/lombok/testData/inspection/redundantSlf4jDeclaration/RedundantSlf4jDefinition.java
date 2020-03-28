import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RedundantSlf4jDefinition {
  <warning descr="Slf4j Logger is defined explicitly. Use Lombok @Slf4j annotation instead.">private static final org.slf4j.Logger LOG1 = org.slf4j.LoggerFactory.getLogger(RedundantSlf4jDefinition.class);</warning>
  <warning descr="Slf4j Logger is defined explicitly. Use Lombok @Slf4j annotation instead.">org.slf4j.Logger LOG2 = org.slf4j.LoggerFactory.getLogger(RedundantSlf4jDefinition.class);</warning>
  <warning descr="Slf4j Logger is defined explicitly. Use Lombok @Slf4j annotation instead.">org.slf4j.Logger LOG3 = org.slf4j.LoggerFactory.getLogger(RedundantSlf4jDefinition.class);</warning>
  <warning descr="Slf4j Logger is defined explicitly. Use Lombok @Slf4j annotation instead.">Logger LOG4 = LoggerFactory.getLogger(RedundantSlf4jDefinition.class);</warning>


  void aMethod() {
    <warning descr="Slf4j Logger is defined explicitly. Use Lombok @Slf4j annotation instead.">final org.slf4j.Logger LOG5 = org.slf4j.LoggerFactory.getLogger(RedundantSlf4jDefinition.class);</warning>
    <warning descr="Slf4j Logger is defined explicitly. Use Lombok @Slf4j annotation instead.">org.slf4j.Logger LOG6 = org.slf4j.LoggerFactory.getLogger(RedundantSlf4jDefinition.class);</warning>
  }
}
