import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Class A implements Externalizable and has its own toString, equals and hashcode methods.
 * Class B extends A and has its own toString, equals and hashcode methods.
 * Ensure that when rearranging class B, its methods show up under 'canonical' -- not "other methods" (bug).
 */
public class NestedOverridesTest
  implements Externalizable
{
  public int hashCode() {
    return super.hashCode();
  }

  public void writeExternal(ObjectOutput objectOutput) throws IOException {
  }

  public boolean equals(Object object) {
    return super.equals(object);
  }

  public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
  }

  public String toString() {
    return "";
  }
}

class B extends NestedOverridesTest {
  public String toString() {
    return super.toString();
  }

  public void writeExternal(ObjectOutput objectOutput) throws IOException {
    super.writeExternal(objectOutput);
  }

  public int hashCode() {
    return super.hashCode();
  }
}