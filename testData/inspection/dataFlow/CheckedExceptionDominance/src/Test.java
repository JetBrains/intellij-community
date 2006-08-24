 public class CheckedExceptionDominance {
   private static class CheckedException extends Exception {}

   public static void foo() {
     boolean flag = true;

     try {
       bar();
     }
     catch (CheckedException e) {
       flag = false;
     }
     catch (Exception e) {
     }

     if (flag) { // This should not be highlighted as always true;
       System.out.println("Must not happen");
     }
   }

   public static void bar() throws CheckedException {
     throw new CheckedException();
   }
 }
