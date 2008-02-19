public class FieldTest {
    private Object comboBox;

    private class NoSelectionComboItem {
        private NoSelectionComboItem() {
        }

        public String getLabel() {
            Object comboItem = <selection>comboBox</selection>.getSelectedItem();
            return comboItem == this ? "<Please Select>" : "<Back to overview>";
        }
    }
}