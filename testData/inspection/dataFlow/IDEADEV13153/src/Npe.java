import org.jetbrains.annotations.*;

import java.util.*;

class Test {
   void foo() {
     String[] data = new String[] {"abs", "def"};
     for (@NotNull String foo: data) {
       assert foo != null; // Condition always true
     }
   }
}