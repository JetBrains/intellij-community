package org.jetbrains.idea.svn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LimitedStringsList {
  private static final int ourMaxTypedUrls = 20;
  private final List<String> myList;

  public LimitedStringsList(final List<String> list) {
    myList = new ArrayList<String>((list.size() > ourMaxTypedUrls ? list.subList(0, ourMaxTypedUrls) : list));
  }

  public void add(final String value) {
    myList.remove(value);

    if (myList.size() >= ourMaxTypedUrls) {
      myList.removeAll(myList.subList(0, (myList.size() - ourMaxTypedUrls + 1)));
    }

    // more recent first
    myList.add(0, value);
  }

  public List<String> getList() {
    return Collections.unmodifiableList(myList);
  }
}
