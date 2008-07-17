public final class ChildClass extends Superclass<ChildClass.Param, ChildClass.Setup>
{
// --- FIELDS ---

   private ChildClass()
   {


   }

// --- OTHER METHODS ---

   @Override
   protected Setup createInitializer()
   {
      return new ChildClass.Setup();
   }


   class Param extends AbstractSetupParams
   {
   }

   private class Setup extends AbstractSetup<Param>
   {

   }

    public static void main(String[] args) {}

    

}