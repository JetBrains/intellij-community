public class Test {
   static Test <caret>method(final Test anObject) {
     final Test[] result = new int[1];
     new Runnable() {
         public void run() {
            result[0] = anObject;
         }         
     }.run();
     return result[0];
   }
}