// FILE: Counter.kt
// NEW_NAME: count
class Counter {
    var hits<caret>: Int = 0
}

// FILE: Caller.java
public class Caller {
  int read(Counter counter) {
    return counter.getHits();
  }
}
