package com.intellij.ui.navigation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class History {

  private List<Place> myHistory = new ArrayList<Place>();
  private int myCurrentPos;
  private Place.Navigator myRoot;

  private boolean myNavigatedNow;

  public History(@NotNull Place.Navigator root) {
    myRoot = root;
  }

  public void pushQueryPlace() {
    if (isNavigatingNow()) return;

    final Place place = query();
    if (place != null) {
      pushPlace(query());
    }
  }

  public void pushPlace(@NotNull Place place) {
    while (myCurrentPos > 0 && myHistory.size() > 0 && myCurrentPos < myHistory.size() - 1) {
      myHistory.remove(myHistory.size() - 1);
    }

    if (myHistory.size() > 0) {
      final Place prev = myHistory.get(myHistory.size() - 1);
      if (prev.equals(place)) return;

      if (prev.isMoreGeneralFor(place)) {
        myHistory.remove(prev);
      }
    }

    addPlace(place);
  }

  private void addPlace(Place place) {
    myHistory.add(place);
    myCurrentPos = myHistory.size() - 1;
  }

  public void pushPlaceForElement(String name, Object value) {
    if (!canNavigateFor(name)) return;

    final Place checkPlace = getCheckPlace(name);
    if (checkPlace == null) return;
    pushPlace(checkPlace.cloneForElement(name, value));
  }

  public Place getPlaceForElement(String name, String value) {
    final Place checkPlace = getCheckPlace(name);
    if (checkPlace == null) return new Place();

    return checkPlace.cloneForElement(name, value);
  }

  public void navigateTo(Place place) {
    myRoot.navigateTo(place);
  }

  public void back() {
    assert canGoBack();
    goThere(myCurrentPos - 1);
  }

  private void goThere(final int nextPos) {
    myNavigatedNow = true;
    final Place next = myHistory.get(nextPos);
    try {
      myRoot.navigateTo(next).doWhenDone(new Runnable() {
        public void run() {
          myCurrentPos = nextPos;
          myNavigatedNow = false;
        }
      });
    }
    catch (Throwable e) {
      myNavigatedNow = false;
      throw new RuntimeException(e);
    }
  }

  public boolean isNavigatingNow() {
    return myNavigatedNow;
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

  public Place query() {
    final Place result = new Place();
    myRoot.queryPlace(result);
    return result;
  }

  private Place getCurrent() {
    if (myCurrentPos >= 0 && myCurrentPos < myHistory.size()) {
      return myHistory.get(myCurrentPos);
    } else {
      return null;
    }
  }

  private boolean canNavigateFor(String pathElement) {
    if (isNavigatingNow()) return false;

    Place checkPlace = getCheckPlace(pathElement);

    return checkPlace != null && checkPlace.getPath(pathElement) != null;
  }

  @Nullable
  private Place getCheckPlace(String pathElement) {
    Place checkPlace = getCurrent();
    if (checkPlace == null || checkPlace.getPath(pathElement) == null) {
      checkPlace = query();
    }

    return checkPlace != null && checkPlace.getPath(pathElement) != null ? checkPlace : null;
  }



}
