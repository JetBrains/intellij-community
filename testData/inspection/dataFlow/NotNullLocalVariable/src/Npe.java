import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Npe {
   void bar() {
     final @NotNull Object o = call();
     if (o == null) {}
   }
   Object call() {return new Object();}
}