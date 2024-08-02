namespace java com.jetbrains.python.console.protocol

/**
*  Script for Java classes generation: /community/python/thrift/console-compile.sh
*/

/**
 * `com.jetbrains.python.console.PydevConsoleCommunication`
 */

/**
 * Corresponds to `PyDebugValue`.
 */
struct DebugValue {
  1: string name,
  2: string type,
  3: string qualifier,
  4: string value,
  5: bool isContainer,
  6: string shape,
  7: bool isReturnedValue,
  8: bool isIPythonHidden,
  9: bool isErrorOnEval,
  10: string typeRendererId
}

typedef list<DebugValue> GetFrameResponse

struct ColHeader {
  1: string label,
  2: string type,
  3: string format,
  4: string max,
  5: string min,
}

struct RowHeader {
  1: string label,
}

struct ArrayHeaders {
  1: list<ColHeader> colHeaders,
  2: list<RowHeader> rowHeaders,
}

struct ArrayData {
  1: i32 rows,
  2: i32 cols,
  3: list<list<string>> data,
}

/**
 * Corresponds to `ArrayChunk`.
 **/
struct GetArrayResponse {
  /**
   * The string representation of the array slice. It is constructed from the
   * name of the array variable and the range.
   *
   * E.g. `array[0:100]`, `matrix[0:3, 0:3]`, `multidimensional[0][0][0:50]`.
   */
  1: string slice,
  2: i32 rows,
  3: i32 cols,
  4: string format,
  5: string type,

  /**
   * `max` could be `True` or `False` or the string representation of a double
   * value that will be parsed using `Double.parseDouble()` method.
   */
  6: string max,

  /**
   * See `max`.
   */
  7: string min,

  8: ArrayHeaders headers,

  9: ArrayData data,
}

typedef i32 LoadFullValueRequestSeq

/**
 * Corresponds to completion types declared in "_pydev_bundle/_pydev_imports_tipper.py".
 */
enum CompletionOptionType {
  IMPORT = 0,
  CLASS = 1,
  FUNCTION = 2,
  ATTR = 3,
  BUILTIN = 4,
  PARAM = 5,
  IPYTHON = 11,
  IPYTHON_MAGIC = 12
}

struct CompletionOption {
  1: string name,
  2: string documentation,

  /**
   * Originaly arguments come in a string `(<arg1>, <arg2>, ...)`.
   */
  3: list<string> arguments,
  4: CompletionOptionType type,
}

typedef list<CompletionOption> GetCompletionsResponse

typedef string AttributeDescription

typedef list<DebugValue> DebugValues

exception UnsupportedArrayTypeException {
  1: string type,
}

exception PythonUnhandledException {
  1: string traceback,
}

exception PythonTableException {
  1: string message,
}

/**
 * Indicates that the related array has more than two dimensions.
 **/
exception ExceedingArrayDimensionsException {
}

service PythonConsoleBackendService {
  /**
   * Returns `true` if Python console script needs more code to evaluate it.
   * Returns `false` if the code is scheduled for evaluation.
   */
  bool execLine(1: string line) throws (1: PythonUnhandledException unhandledException),

  /**
   * Returns `true` if Python console script needs more code to evaluate it.
   * Returns `false` if the code is scheduled for evaluation.
   */
  bool execMultipleLines(1: string lines) throws (1: PythonUnhandledException unhandledException),

  GetCompletionsResponse getCompletions(1: string text, 2: string actTok) throws (1: PythonUnhandledException unhandledException),

  /**
   * The description of the given attribute in the shell.
   */
  AttributeDescription getDescription(1: string text),

  /**
   * Return Frame
   */
  GetFrameResponse getFrame(1: i32 type) throws (1: PythonUnhandledException unhandledException),

  /**
   * Parameter is a full path in a variables tree from the top-level parent to the debug value.
   **/
  DebugValues getVariable(1: string variable) throws (1: PythonUnhandledException unhandledException),

  /**
     * Parameter is a serialized user type renderers.
     **/
    bool setUserTypeRenderers(1: string renderers) throws (1: PythonUnhandledException unhandledException),

  /**
   * Changes the variable value asynchronously.
   */
  void changeVariable(1: string evaluationExpression, 2: string value) throws (1: PythonUnhandledException unhandledException),

  void connectToDebugger(1: i32 localPort, 2: string host, 3: map<string, bool> opts, 4: map<string, string> extraEnvs)
  throws (1: PythonUnhandledException unhandledException),

  void interrupt(),

  /**
   * Should normally return "PyCharm" string.
   */
  string handshake(),

  /**
   * Closes Python console script.
   */
  oneway void close(),

  DebugValues evaluate(1: string expression, 2: bool doTrunc) throws (1: PythonUnhandledException unhandledException),

  GetArrayResponse getArray(1: string vars, 2: i32 rowOffset, 3: i32 colOffset, 4: i32 rows, 5: i32 cols, 6: string format)
    throws (1: UnsupportedArrayTypeException unsupported, 2: ExceedingArrayDimensionsException exceedingDimensions,
    3: PythonUnhandledException unhandledException),

  void execDataViewerAction(1: string varName, 2: string action, 3: string myArgs)
    throws (1: PythonUnhandledException unhandledException),

  /**
   * The result is returned asyncronously with `PythonConsoleFrontendService.returnFullValue`.
   */
  void loadFullValue(1: LoadFullValueRequestSeq seq, 2: list<string> variables) throws (1: PythonUnhandledException unhandledException),

  string execTableCommand(1: string tableVariable, 2: string commandType, 3: string startIndex, 4: string endIndex, 5: string format) throws (1: PythonUnhandledException unhandledException, 2: PythonTableException tableException)
}

exception KeyboardInterruptException {
}

service PythonConsoleFrontendService {
  void notifyFinished(1: bool needsMoreInput, 2: bool exceptionOccurred),

  string requestInput(1: string path) throws (1: KeyboardInterruptException interrupted),

  void notifyAboutMagic(1: list<string> commands, 2: bool isAutoMagic),

  void showConsole(),

  /**
   * Returns the result for `PythonConsoleBackendService.loadFullValue`.
   */
  void returnFullValue(1: LoadFullValueRequestSeq requestSeq, 2: list<DebugValue> response),

  bool IPythonEditor(1: string path, 2: string line),

  void sendRichOutput(1: map<string, string> data),
}