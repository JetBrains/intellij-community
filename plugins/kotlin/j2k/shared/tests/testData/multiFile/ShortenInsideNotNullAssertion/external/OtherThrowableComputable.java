package other;

interface ThrowableComputable<T, E extends Throwable> {
    T compute() throws E;
}
