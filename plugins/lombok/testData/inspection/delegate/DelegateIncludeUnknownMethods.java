import lombok.experimental.Delegate;

import java.util.ArrayList;
import java.util.Collection;

public class DelegateIncludeUnknownMethods {

  private interface SomeCollection {
    boolean add(String item);

    boolean remove(Object item);

    boolean clearXYZ();

    boolean abcdXYZ();
  }

  private interface SomeExcludeCollection {
    boolean remove(Object item);

    boolean contains(Object o);

    boolean containsAll(Object o1, Object o2);
  }

  <warning descr="Cannot find method in target type: 'containsAll'"><warning descr="Cannot find methods in target type: 'clearXYZ and abcdXYZ'">@Delegate(types = SomeCollection.class, excludes = SomeExcludeCollection.class)</warning></warning>
  private final Collection<String> collection = new ArrayList<String>();

  public static void main(String[] args) {
    final DelegateIncludeUnknownMethods delegate = new DelegateIncludeUnknownMethods();
    delegate.add("someItem");
    delegate.<error descr="Cannot resolve method 'remove' in 'DelegateIncludeUnknownMethods'">remove</error>("someItem"); // invalid (excluded)
    delegate.<error descr="Cannot resolve method 'contains' in 'DelegateIncludeUnknownMethods'">contains</error>("someItem"); // invalid (not included)

    //delegate.clearXYZ();// invalid
  }
}