import org.jetbrains.NotNull;
import org.jetbrains.Nullable;

public class Npe {
   Object foo(@NotNull Object o) {
     return o;
   }

   @Nullable Object nullable() {
     return null;
   }

   void bar() {
     Object o = nullable();
     if (o != null) {
       foo(o); // OK, o can't be null.
     }
   }
}