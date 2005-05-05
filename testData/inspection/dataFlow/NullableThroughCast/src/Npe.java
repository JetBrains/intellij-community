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
     Object o = foo((Object)nullable()); // null should not be passed here.
   }
}