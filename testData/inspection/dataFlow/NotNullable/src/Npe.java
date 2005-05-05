import org.jetbrains.annotations.NotNull;

public class Npe {
   @NotNull Object foo() {
     return new Object();
   }

   void bar() {
     Object o = foo();
     if (o == null) System.out.println("Can't be");
   }
}