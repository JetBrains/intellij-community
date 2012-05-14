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
// ------------------------ CANONICAL METHODS ------------------------

  public boolean equals(Object object) {
    return super.equals(object);
  }

  public int hashCode() {
    return super.hashCode();
  }

  public String toString() {
    return "";
  }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Externalizable ---------------------

  public void writeExternal(ObjectOutput objectOutput) throws IOException {
  }

  public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
  }
}

class B extends NestedOverridesTest {
// ------------------------ CANONICAL METHODS ------------------------

  public int hashCode() {
    return super.hashCode();
  }

  public String toString() {
    return super.toString();
  }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Externalizable ---------------------

  public void writeExternal(ObjectOutput objectOutput) throws IOException {
    super.writeExternal(objectOutput);
  }
}
