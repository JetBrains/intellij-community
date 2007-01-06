public final class Foo {
   private Object f1 = new Object(); // Can be final but unused
   private Object f2;  // Can be final
   private Object f3;  // Cannot be final
   private Object f4;  // cannot
   private Object f5 = new Object();  // cannot

 
   public Foo() {
        f2 = new Object();
        try {
            f3 = "test";
        } catch (Exception e) {
        }
        f5 = new Object();
   }

   public Foo(int i) {
       f2 = new Object();
       f4 = new Object();
       f5 = new Object();
   }
}