import java.util.ArrayList;
import java.util.List;

public class SuperBuilderWithBuilderDefault {

    private List<String> listItems = new ArrayList<>();

    protected SuperBuilderWithBuilderDefault(SuperBuilderWithBuilderDefault.SuperBuilderWithBuilderDefaultBuilder<?, ?> b) {
        if (b.listItems$set) {
            this.listItems = b.listItems$value;
        } else {
            this.listItems = $default$listItems();
        }
    }

    private static List<String> $default$listItems() {
        return new ArrayList<>();
    }

    public static SuperBuilderWithBuilderDefaultBuilder<?, ?> builder() {
        return new SuperBuilderWithBuilderDefaultBuilderImpl();
    }

    public static abstract class SuperBuilderWithBuilderDefaultBuilder<C extends SuperBuilderWithBuilderDefault, B extends SuperBuilderWithBuilderDefaultBuilder<C, B>> {
        private List<String> listItems$value;
        private boolean listItems$set;

        public B listItems(List<String> listItems) {
            this.listItems$value = listItems;
            this.listItems$set = true;
            return self();
        }

        protected abstract B self();

        public abstract C build();

        public String toString() {
            return "SuperBuilderWithBuilderDefault.SuperBuilderWithBuilderDefaultBuilder(listItems$value=" + this.listItems$value + ")";
        }
    }

    private static final class SuperBuilderWithBuilderDefaultBuilderImpl extends SuperBuilderWithBuilderDefaultBuilder<SuperBuilderWithBuilderDefault, SuperBuilderWithBuilderDefaultBuilderImpl> {
        private SuperBuilderWithBuilderDefaultBuilderImpl() {
        }

        protected SuperBuilderWithBuilderDefaultBuilderImpl self() {
            return this;
        }

        public SuperBuilderWithBuilderDefault build() {
            return new SuperBuilderWithBuilderDefault(this);
        }
    }
}