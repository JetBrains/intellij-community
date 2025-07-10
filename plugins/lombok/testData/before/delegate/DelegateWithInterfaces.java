import lombok.experimental.Delegate;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

class ItemsList<T> implements List<T> {
  @Delegate
  private final List<T> items;
  private final String additionalData;

  ItemsList(List<T> items, String additionalData) {
    this.items = items;
    this.additionalData = additionalData;
  }
}

public class DelegateWithInterfaces {
  public List<String> test() {
    var list = new ItemsList<String>(Arrays.asList("S1", "S2"), "data");
    return list.stream().map(String::toLowerCase).collect(Collectors.toList());
  }
}