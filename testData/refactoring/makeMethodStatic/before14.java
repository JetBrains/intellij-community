public class Test {
   Test <caret>method() {
     final Test[] result = new int[1];
     new Runnable() {
         public void run() {
            result[0] = Test.this;
         }         
     }.run();
     return result[0];
   }
}