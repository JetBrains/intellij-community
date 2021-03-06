import java.util.HashSet;
import java.util.Set;


/**
 * Common Pizza Interface
 *
 * @see Margherita
 */
interface Pizza {
    /** The size of {@link Pizza} */
    int getSize();

    /**
     * Human-readable name of type {@link java.lang.String stringLabel}
     *
     * @throws IllegalStateException
     * @throws ClassCastException
     */
    String getName();

    /**
     * Checks whether pizza contains the specified {@link Pizza.Ingredient}
     *
     * @param ingredient the ingredient to check
     * @exception java.lang.UnsupportedOperationException
     * @see Margherita#contains
     */
    boolean contains(Ingredient ingredient);

    /** Ingredient of {@link Pizza} */
    interface Ingredient {}

    /**
     * Abstract {@link Pizza} builder
     *
     * @param <P> well-known hack to solve abstract builder chain problem, see {@link Builder#self}
     */
    abstract class Builder<P extends Builder<P>> {
        private Set<Ingredient> ingredients = new HashSet<>();

        /**
         * Adds <code>ingredient</code> to the {@link Builder#ingredients}
         *
         * @return value of type {@link P}
         */
        abstract P addIngredient(Pizza.Ingredient ingredient);
        abstract Pizza build();
        protected abstract P self();
    }
}

/**
 * Pizza Margherita
 *
 * @see Pizza
 */
class Margherita implements Pizza {
    /**
     * {@link Margherita#getSize} ideal size of {@link Margherita} is of course 42
     */
    @Override
    public int getSize() {
        return 42;
    }

    @Override
    public String getName() {
        return new String("Margherita");
    }

    /**
     * Checks whether pizza contains the specified {@link Pizza.Ingredient}
     *
     * @param ingredient see {@link Pizza.Ingredient}
     */
    @Override
    public boolean contains(Ingredient ingredient) {
        return false;
    }
}