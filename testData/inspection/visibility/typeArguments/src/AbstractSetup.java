public abstract class AbstractSetup<T extends AbstractSetupParams>
{
   private T parameters;

   public T getParameters()
   {
      return parameters;
   }

   protected void setParameters(T pParameters)
   {
      parameters = pParameters;
   }
}
