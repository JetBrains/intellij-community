package one;


public class Hierarchy extends Proxy {
  void classMembers() {
    this.getProperty();

    int fieldProperty = this.fieldProperty;

    this.getVariable();
    this.setVariable(42);

    this.fieldVariable = 4;
    int fieldVariable = this.fieldVariable;

    Custom lateinitVariable = this.lateinitVariable;
    this.lateinitVariable = new Custom();
    this.setLateinitVariable(lateinitVariable);
    this.getLateinitVariable();
    
    this.simpleFunction(42);

    this.extension("awd");
  }
  
  //void companionMembers() {
  //  Companion.getCompanionproperty();
  //
  //  int companionfieldProperty1 = companionfieldProperty;
  //
  //  getCompanionstaticProperty();
  //
  //  int companionconstProperty1 = companionconstProperty;
  //
  //  Companion.getCompanionvariable();
  //  Companion.setCompanionvariable(4);
  //
  //  int companionfieldVariable1 = companionfieldVariable;
  //  companionfieldVariable = 4;
  //
  //  getCompanionstaticVariable();
  //  setCompanionstaticVariable(4);
  //
  //  Custom companionlateinitVariable = KotlinOnlyClass.companionlateinitVariable;
  //  Companion.getCompanionlateinitVariable();
  //  Companion.setCompanionlateinitVariable(companionlateinitVariable);
  //
  //  Custom companionlateinitStaticVariable = KotlinOnlyClass.companionlateinitStaticVariable;
  //  getCompanionlateinitStaticVariable();
  //  setCompanionlateinitStaticVariable(companionlateinitStaticVariable);
  //
  //  Companion.companionsimpleFunction(4);
  //
  //  companionstaticFunction(4);
  //
  //  Companion.companionextension("");
  //
  //  companionstaticExtension("42");
  //}
}
