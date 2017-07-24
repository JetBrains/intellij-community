package slowCheck;

/**
 * @author peter
 */
public class GeneratorException extends RuntimeException {

  GeneratorException(Iteration<?> iteration, Throwable cause) {
    super("Exception while generating data, " + iteration.printSeeds(), cause);
  }
}
