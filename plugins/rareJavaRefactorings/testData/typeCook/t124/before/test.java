interface Map<K, V> {
}

class THashMap implements Map {
}

class Test {
  void f() {
    Map[] readVariables = null;
    readVariables[0] = new THashMap();
  }
}
