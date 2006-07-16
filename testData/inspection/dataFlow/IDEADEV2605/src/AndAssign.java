public class AndAssign {
  public void foo(boolean result, Object acc) {
    result &= verify(result ? acc : null);
  }

  public boolean verify(Object o) {
    System.out.println(o);
    return true;
  }

  public void positives() {
     boolean t = true;
     boolean f = false;
     boolean r;

     r = t;
     r &= t;                      // Always true

     if (r) {                     // Always true
        System.out.println("foo");
     }

     r = t;
     r &= f;                      // Always true

     if (r) {                     // Always false
        System.out.println("foo");
     }

     r = f;
     r &= t;                      // Always false

     if (r) {                     // Always false
        System.out.println("foo");
     }

     r = f;
     r &= f;                      // Always false

     if (r) {                     // Always false
        System.out.println("foo");
     }

     r = t;
     r |= t                       // Always true

     if (r) {                     // Always true
        System.out.println("foo");
     }

     r = t;
     r |= f                       // Always true

     if (r) {                     // Always true
        System.out.println("foo");
     }

     r = f;
     r |= t                       // Always false

     if (r) {                     // Always true
        System.out.println("foo");
     }

     r = f;
     r |= f                      // Always false

     if (r) {                     // Always false
        System.out.println("foo");
     }
  }
}