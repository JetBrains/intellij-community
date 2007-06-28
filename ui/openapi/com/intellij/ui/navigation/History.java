package com.intellij.ui.navigation;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class History {

  private List<Place> myHistory = new ArrayList<Place>();
  private int myCurrentPos;


  public void pushPlace(@NotNull Place place) {
    while (myCurrentPos > 0 && myHistory.size() > 0 && myCurrentPos < myHistory.size() - 1) {
      myHistory.remove(myHistory.size() - 1);
    }

    if (myHistory.size() > 0) {
      if (myHistory.get(myHistory.size() - 1).equals(place)) return;
    }

    myHistory.add(place);
    myCurrentPos = myHistory.size() - 1;
  }

  public void back() {
    assert canGoBack();
    goThere(myCurrentPos - 1);
  }

  private void goThere(final int nextPos) {
    final Place next = myHistory.get(nextPos);
    next.goThere();
    myCurrentPos = nextPos;
  }

  public boolean canGoBack() {
    return myHistory.size() > 1 && myCurrentPos > 0;
  }

  public void forward() {
    assert canGoForward();
    goThere(myCurrentPos + 1);
  }

  public boolean canGoForward() {
    return myHistory.size() > 1 && myCurrentPos < myHistory.size() - 1;
  }

  public void clear() {
    myHistory.clear();
    myCurrentPos = -1;
  }
}
