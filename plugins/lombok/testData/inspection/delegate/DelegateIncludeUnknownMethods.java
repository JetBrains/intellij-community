import lombok.experimental.Delegate;

import java.util.ArrayList;
import java.util.Collection;

public class DelegateIncludeUnknownMethods {

  private interface SomeCollection {
    boolean add(String item);

    boolean remove(Object item);

    boolean clearXYZ();
  }

  <warning descr="Cannot find symbol method clearXYZ">@Delegate(types = SomeCollection.class)</warning>
  private final Collection<String> collection = new ArrayList<String>();

  public static void main(String[] args) {
    final DelegateIncludeUnknownMethods delegate = new DelegateIncludeUnknownMethods();
    delegate.add("someItem");
    delegate.remove("someItem");

    //delegate.clearXYZ();// invalid
  }
}