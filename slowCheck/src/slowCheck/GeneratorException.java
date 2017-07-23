package slowCheck;

/**
 * @author peter
 */
public class GeneratorException extends RuntimeException {

  GeneratorException(long seed, Throwable cause) {
    super("Exception while generating data, seed=" + seed, cause);
  }
}
