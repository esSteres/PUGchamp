package bot;

@FunctionalInterface
public interface ExceptingBiFunction<T, U, R> {
    public R apply (T t, U u) throws Exception;
}
