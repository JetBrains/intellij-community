public final class Junk
   {
   public void sillyMethod()
      {
      String bar = "bar";
      try
         {
         int i = 1;
         String foo = "foo";
         System.out.println("[" + bar + "|" + i + "|" + foo + "]");
         //throw new Exception();
         }
      catch (Exception e)
         {
         int j = 2;
         System.out.println("j = [" + j + "]");
         }
      }
   }

