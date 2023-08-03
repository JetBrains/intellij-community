class C1 protected constructor(val x: Any) {}

fun f() {
  val c = C1(12);
}

// IGNORE_K2