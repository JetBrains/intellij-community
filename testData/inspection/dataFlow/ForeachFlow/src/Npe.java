public class Npe {
   Object foo(Object[] objs) {
      boolean skip = true;
      for (Object o : objs) {
        if (o instanceof String) {
          skip = false;
          continue;
        }

        if (skip) {
          continue;
        }
        bar();
      }

      return "";
   }

   void bar() {
   }
}