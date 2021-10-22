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
  
  //void companionMembers(Proxy proxy) {
  //  proxy.Companion.getCompanionproperty();
  //
  //  int companionfieldProperty1 = proxy.companionfieldProperty;
  //
  //  proxy.getCompanionstaticProperty();
  //
  //  int companionconstProperty1 = proxy.companionconstProperty;
  //
  //  proxy.Companion.getCompanionvariable();
  //  proxy.Companion.setCompanionvariable(4);
  //
  //  int companionfieldVariable1 = proxy.companionfieldVariable;
  //  proxy.companionfieldVariable = 4;
  //
  //  proxy.getCompanionstaticVariable();
  //  proxy.setCompanionstaticVariable(4);
  //
  //  Custom companionlateinitVariable = KotlinOnlyClass.companionlateinitVariable;
  //  proxy.Companion.getCompanionlateinitVariable();
  //  proxy.Companion.setCompanionlateinitVariable(companionlateinitVariable);
  //
  //  Custom companionlateinitStaticVariable = KotlinOnlyClass.companionlateinitStaticVariable;
  //  proxy.getCompanionlateinitStaticVariable();
  //  proxy.setCompanionlateinitStaticVariable(companionlateinitStaticVariable);
  //
  //  proxy.Companion.companionsimpleFunction(4);
  //
  //  proxy.companionstaticFunction(4);
  //
  //  proxy.Companion.companionextension("");
  //
  //  proxy.companionstaticExtension("42");
  //}
}
