package one;


public class Parameter {
  void classMembers(Proxy proxy) {
    proxy.getProperty();

    int fieldProperty = proxy.fieldProperty;

    proxy.getVariable();
    proxy.setVariable(42);

    proxy.fieldVariable = 4;
    int fieldVariable = proxy.fieldVariable;

    Custom lateinitVariable = proxy.lateinitVariable;
    proxy.lateinitVariable = new Custom();
    proxy.setLateinitVariable(lateinitVariable);
    proxy.getLateinitVariable();
    
    proxy.simpleFunction(42);

    proxy.extension("awd");
  }
}
