public class SomeClass {
    private static class Item {
        private final String module;
        private String option;

        Item(String module) {
            this(module, "");
        }

        private Item(String module, String option) {
            this.module = module;
            this.option = option;
        }
    }

    private final List<Item> myItems = new ArrayList<>();

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        Item item = myItems.get(rowIndex);
        item.option = ((String) aValue).trim();
    }
}