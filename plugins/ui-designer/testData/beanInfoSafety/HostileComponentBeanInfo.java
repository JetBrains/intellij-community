import java.beans.SimpleBeanInfo;

/**
 * {@code java.beans.Introspector} finds this class by name and instantiates it, which is the code
 * execution vector reported in IDEA-392515. The static initializer records that it ran.
 */
public class HostileComponentBeanInfo extends SimpleBeanInfo {
  static {
    Payload.record("bean-info");
  }

  public HostileComponentBeanInfo() {
  }
}
