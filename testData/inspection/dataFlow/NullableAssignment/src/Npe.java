import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Npe {
   @NotNull Object aField;
   @Nullable Object nullable() {
     return null;
   }

   void bar() {
     Object o = nullable();
     aField = o;
     @NotNull Object aLocalVariable = o;
   }
}