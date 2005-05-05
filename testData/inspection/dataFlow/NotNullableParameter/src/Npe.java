import org.jetbrains.annotations.NotNull;

public class Npe {
   Object foo(@NotNull Object o) {
     return o;
   }

   void bar() {
     Object o = foo(null); // null should not be passed here.
   }
}