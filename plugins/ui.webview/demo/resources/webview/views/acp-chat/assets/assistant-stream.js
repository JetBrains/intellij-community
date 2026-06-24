import { i as __toESM, t as __commonJSMin } from "./rolldown-runtime.js";
//#region node_modules/assistant-stream/dist/utils/promiseWithResolvers.js
var promiseWithResolvers = () => {
	let resolve;
	let reject;
	const promise = new Promise((res, rej) => {
		resolve = res;
		reject = rej;
	});
	if (!resolve || !reject) throw new Error("Failed to create promise");
	return {
		promise,
		resolve,
		reject
	};
};
//#endregion
//#region node_modules/assistant-stream/dist/core/utils/stream/merge.js
var createMergeStream = () => {
	const list = [];
	let sealed = false;
	let controller;
	let currentPull;
	const handlePull = (item) => {
		if (!item.promise) item.promise = item.reader.read().then(({ done, value }) => {
			item.promise = void 0;
			if (done) {
				list.splice(list.indexOf(item), 1);
				if (sealed && list.length === 0) controller.close();
			} else controller.enqueue(value);
			currentPull?.resolve();
			currentPull = void 0;
		}).catch((e) => {
			console.error(e);
			list.forEach((item) => {
				item.reader.cancel();
			});
			list.length = 0;
			controller.error(e);
			currentPull?.reject(e);
			currentPull = void 0;
		});
	};
	return {
		readable: new ReadableStream({
			start(c) {
				controller = c;
			},
			pull() {
				currentPull = promiseWithResolvers();
				list.forEach((item) => {
					handlePull(item);
				});
				return currentPull.promise;
			},
			cancel() {
				list.forEach((item) => {
					item.reader.cancel();
				});
				list.length = 0;
			}
		}),
		isSealed() {
			return sealed;
		},
		seal() {
			sealed = true;
			if (list.length === 0) controller.close();
		},
		addStream(stream) {
			if (sealed) throw new Error("Cannot add streams after the run callback has settled.");
			const item = { reader: stream.getReader() };
			list.push(item);
			handlePull(item);
		},
		enqueue(chunk) {
			this.addStream(new ReadableStream({ start(c) {
				c.enqueue(chunk);
				c.close();
			} }));
		}
	};
};
//#endregion
//#region node_modules/assistant-stream/dist/core/modules/text.js
var TextStreamControllerImpl = class {
	_controller;
	_isClosed = false;
	constructor(controller) {
		this._controller = controller;
	}
	append(textDelta) {
		this._controller.enqueue({
			type: "text-delta",
			path: [],
			textDelta
		});
		return this;
	}
	close() {
		if (this._isClosed) return;
		this._isClosed = true;
		this._controller.enqueue({
			type: "part-finish",
			path: []
		});
		this._controller.close();
	}
};
var createTextStream = (readable) => {
	return new ReadableStream({
		start(c) {
			return readable.start?.(new TextStreamControllerImpl(c));
		},
		pull(c) {
			return readable.pull?.(new TextStreamControllerImpl(c));
		},
		cancel(c) {
			return readable.cancel?.(c);
		}
	});
};
var createTextStreamController = () => {
	let controller;
	return [createTextStream({ start(c) {
		controller = c;
	} }), controller];
};
//#endregion
//#region node_modules/assistant-stream/dist/core/modules/tool-call.js
var ToolCallStreamControllerImpl = class {
	_controller;
	_isClosed = false;
	_mergeTask;
	constructor(_controller) {
		this._controller = _controller;
		const stream = createTextStream({ start: (c) => {
			this._argsTextController = c;
		} });
		let hasArgsText = false;
		this._mergeTask = stream.pipeTo(new WritableStream({ write: (chunk) => {
			switch (chunk.type) {
				case "text-delta":
					hasArgsText = true;
					this._controller.enqueue(chunk);
					break;
				case "part-finish":
					if (!hasArgsText) this._controller.enqueue({
						type: "text-delta",
						textDelta: "{}",
						path: []
					});
					this._controller.enqueue({
						type: "tool-call-args-text-finish",
						path: []
					});
					break;
				default: throw new Error(`Unexpected chunk type: ${chunk.type}`);
			}
		} }));
	}
	get argsText() {
		return this._argsTextController;
	}
	_argsTextController;
	async setResponse(response) {
		this._argsTextController.close();
		await Promise.resolve();
		this._controller.enqueue({
			type: "result",
			path: [],
			...response.artifact !== void 0 ? { artifact: response.artifact } : {},
			result: response.result,
			isError: response.isError ?? false,
			...response.modelContent !== void 0 ? { modelContent: response.modelContent } : {},
			...response.messages !== void 0 ? { messages: response.messages } : {}
		});
	}
	async close() {
		if (this._isClosed) return;
		this._isClosed = true;
		this._argsTextController.close();
		await this._mergeTask;
		this._controller.enqueue({
			type: "part-finish",
			path: []
		});
		this._controller.close();
	}
};
var createToolCallStream = (readable) => {
	return new ReadableStream({
		start(c) {
			return readable.start?.(new ToolCallStreamControllerImpl(c));
		},
		pull(c) {
			return readable.pull?.(new ToolCallStreamControllerImpl(c));
		},
		cancel(c) {
			return readable.cancel?.(c);
		}
	});
};
var createToolCallStreamController = () => {
	let controller;
	return [createToolCallStream({ start(c) {
		controller = c;
	} }), controller];
};
//#endregion
//#region node_modules/assistant-stream/dist/core/utils/Counter.js
var Counter = class {
	value = -1;
	up() {
		return ++this.value;
	}
};
//#endregion
//#region node_modules/assistant-stream/dist/core/utils/stream/path-utils.js
var PathAppendEncoder = class extends TransformStream {
	constructor(idx) {
		super({ transform(chunk, controller) {
			controller.enqueue({
				...chunk,
				path: [idx, ...chunk.path]
			});
		} });
	}
};
TransformStream;
var PathMergeEncoder = class extends TransformStream {
	constructor(counter) {
		const innerCounter = new Counter();
		const mapping = /* @__PURE__ */ new Map();
		super({ transform(chunk, controller) {
			if (chunk.type === "part-start" && chunk.path.length === 0) mapping.set(innerCounter.up(), counter.up());
			const [idx, ...path] = chunk.path;
			if (idx === void 0) {
				controller.enqueue(chunk);
				return;
			}
			const mappedIdx = mapping.get(idx);
			if (mappedIdx === void 0) throw new Error("Path not found");
			controller.enqueue({
				...chunk,
				path: [mappedIdx, ...path]
			});
		} });
	}
};
//#endregion
//#region node_modules/assistant-stream/dist/core/utils/stream/PipeableTransformStream.js
var PipeableTransformStream = class extends TransformStream {
	constructor(transform) {
		super();
		const readable = transform(super.readable);
		Object.defineProperty(this, "readable", {
			value: readable,
			writable: false
		});
	}
};
//#endregion
//#region node_modules/assistant-stream/dist/core/utils/stream/AssistantMetaTransformStream.js
var AssistantMetaTransformStream = class extends TransformStream {
	constructor() {
		const parts = [];
		super({ transform(chunk, controller) {
			if (chunk.type === "part-start") {
				if (chunk.path.length !== 0) {
					controller.error(/* @__PURE__ */ new Error("Nested parts are not supported"));
					return;
				}
				parts.push(chunk.part);
				controller.enqueue(chunk);
				return;
			}
			if (chunk.type === "text-delta" || chunk.type === "result" || chunk.type === "part-finish" || chunk.type === "tool-call-args-text-finish") {
				if (chunk.path.length !== 1) {
					controller.error(/* @__PURE__ */ new Error(`${chunk.type} chunks must have a path of length 1`));
					return;
				}
				const idx = chunk.path[0];
				if (idx < 0 || idx >= parts.length) {
					controller.error(/* @__PURE__ */ new Error(`Invalid path index: ${idx}`));
					return;
				}
				const part = parts[idx];
				controller.enqueue({
					...chunk,
					meta: part
				});
				return;
			}
			controller.enqueue(chunk);
		} });
	}
};
//#endregion
//#region node_modules/nanoid/non-secure/index.js
var customAlphabet = (alphabet, defaultSize = 21) => {
	return (size = defaultSize) => {
		let id = "";
		let i = size | 0;
		while (i--) id += alphabet[Math.random() * alphabet.length | 0];
		return id;
	};
};
//#endregion
//#region node_modules/assistant-stream/dist/core/utils/generateId.js
var generateId = customAlphabet("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz", 7);
//#endregion
//#region node_modules/assistant-stream/dist/core/modules/assistant-stream.js
var AssistantStreamControllerImpl = class AssistantStreamControllerImpl {
	_state;
	_parentId;
	constructor(state) {
		this._state = state || {
			merger: createMergeStream(),
			contentCounter: new Counter()
		};
	}
	get __internal_isClosed() {
		return this._state.merger.isSealed();
	}
	__internal_getReadable() {
		return this._state.merger.readable;
	}
	__internal_subscribeToClose(callback) {
		this._state.closeSubscriber = callback;
	}
	_addPart(part, stream) {
		if (this._state.append) {
			this._state.append.controller.close();
			this._state.append = void 0;
		}
		this.enqueue({
			type: "part-start",
			part,
			path: []
		});
		this._state.merger.addStream(stream.pipeThrough(new PathAppendEncoder(this._state.contentCounter.value)));
	}
	merge(stream) {
		this._state.merger.addStream(stream.pipeThrough(new PathMergeEncoder(this._state.contentCounter)));
	}
	appendText(textDelta) {
		if (this._state.append?.kind !== "text" || this._state.append.parentId !== this._parentId) this._state.append = {
			kind: "text",
			parentId: this._parentId,
			controller: this.addTextPart()
		};
		this._state.append.controller.append(textDelta);
	}
	appendReasoning(textDelta) {
		if (this._state.append?.kind !== "reasoning" || this._state.append.parentId !== this._parentId) this._state.append = {
			kind: "reasoning",
			parentId: this._parentId,
			controller: this.addReasoningPart()
		};
		this._state.append.controller.append(textDelta);
	}
	addTextPart() {
		const [stream, controller] = createTextStreamController();
		this._addPart(this._withParentIdOption({ type: "text" }), stream);
		return controller;
	}
	addReasoningPart() {
		const [stream, controller] = createTextStreamController();
		this._addPart(this._withParentIdOption({ type: "reasoning" }), stream);
		return controller;
	}
	addToolCallPart(options) {
		const opt = typeof options === "string" ? { toolName: options } : options;
		const toolName = opt.toolName;
		const toolCallId = opt.toolCallId ?? generateId();
		const [stream, controller] = createToolCallStreamController();
		this._addPart({
			type: "tool-call",
			toolName,
			toolCallId,
			...this._parentId && { parentId: this._parentId }
		}, stream);
		if (opt.argsText !== void 0) {
			controller.argsText.append(opt.argsText);
			controller.argsText.close();
		}
		if (opt.args !== void 0) {
			controller.argsText.append(JSON.stringify(opt.args));
			controller.argsText.close();
		}
		if (opt.response !== void 0) controller.setResponse(opt.response);
		return controller;
	}
	_finishedPartStream() {
		return new ReadableStream({ start(controller) {
			controller.enqueue({
				type: "part-finish",
				path: []
			});
			controller.close();
		} });
	}
	_withParentIdOption(options) {
		if (!this._parentId) return options;
		return {
			...options,
			parentId: this._parentId
		};
	}
	appendSource(options) {
		this._addPart(this._withParentIdOption(options), this._finishedPartStream());
	}
	appendFile(options) {
		this._addPart(this._withParentIdOption(options), this._finishedPartStream());
	}
	appendData(options) {
		this._addPart(this._withParentIdOption(options), this._finishedPartStream());
	}
	enqueue(chunk) {
		this._state.merger.enqueue(chunk);
		if (chunk.type === "part-start" && chunk.path.length === 0) this._state.contentCounter.up();
	}
	withParentId(parentId) {
		const controller = new AssistantStreamControllerImpl(this._state);
		controller._parentId = parentId;
		return controller;
	}
	close() {
		this._state.append?.controller?.close();
		this._state.merger.seal();
		this._state.closeSubscriber?.();
	}
};
/**
* Creates an {@link AssistantStream} and writes to it with an
* {@link AssistantStreamController}.
*
* The callback may write synchronously or asynchronously. If it throws, an
* `error` chunk is emitted before the error is rethrown; when the callback
* settles, the stream is closed automatically unless the controller was
* already closed.
*/
function createAssistantStream(callback) {
	const controller = new AssistantStreamControllerImpl();
	const runTask = async () => {
		try {
			await callback(controller);
		} catch (e) {
			if (!controller.__internal_isClosed) controller.enqueue({
				type: "error",
				path: [],
				error: String(e)
			});
			throw e;
		} finally {
			if (!controller.__internal_isClosed) controller.close();
		}
	};
	runTask();
	return controller.__internal_getReadable();
}
/**
* Creates an {@link AssistantStream} together with the controller used to
* write into it.
*
* Use this when the stream needs to be returned before all writers are known.
* Closing the returned controller finishes the paired stream.
*/
function createAssistantStreamController() {
	const { resolve, promise } = promiseWithResolvers();
	let controller;
	return [createAssistantStream((c) => {
		controller = c;
		controller.__internal_subscribeToClose(resolve);
		return promise;
	}), controller];
}
//#endregion
//#region node_modules/assistant-stream/dist/utils/json/fix-json.js
function fixJson(input) {
	const stack = ["ROOT"];
	let lastValidIndex = -1;
	let literalStart = null;
	const path = [];
	let currentKey;
	function pushCurrentKeyToPath() {
		if (currentKey !== void 0) {
			path.push(JSON.parse(`"${currentKey}"`));
			currentKey = void 0;
		}
	}
	function processValueStart(char, i, swapState) {
		switch (char) {
			case "\"":
				lastValidIndex = i;
				stack.pop();
				stack.push(swapState);
				stack.push("INSIDE_STRING");
				pushCurrentKeyToPath();
				break;
			case "f":
			case "t":
			case "n":
				lastValidIndex = i;
				literalStart = i;
				stack.pop();
				stack.push(swapState);
				stack.push("INSIDE_LITERAL");
				break;
			case "-":
				stack.pop();
				stack.push(swapState);
				stack.push("INSIDE_NUMBER");
				pushCurrentKeyToPath();
				break;
			case "0":
			case "1":
			case "2":
			case "3":
			case "4":
			case "5":
			case "6":
			case "7":
			case "8":
			case "9":
				lastValidIndex = i;
				stack.pop();
				stack.push(swapState);
				stack.push("INSIDE_NUMBER");
				pushCurrentKeyToPath();
				break;
			case "{":
				lastValidIndex = i;
				stack.pop();
				stack.push(swapState);
				stack.push("INSIDE_OBJECT_START");
				pushCurrentKeyToPath();
				break;
			case "[":
				lastValidIndex = i;
				stack.pop();
				stack.push(swapState);
				stack.push("INSIDE_ARRAY_START");
				pushCurrentKeyToPath();
				break;
		}
	}
	function processAfterObjectValue(char, i) {
		switch (char) {
			case ",":
				stack.pop();
				stack.push("INSIDE_OBJECT_AFTER_COMMA");
				break;
			case "}":
				lastValidIndex = i;
				stack.pop();
				currentKey = path.pop();
				break;
		}
	}
	function processAfterArrayValue(char, i) {
		switch (char) {
			case ",":
				stack.pop();
				stack.push("INSIDE_ARRAY_AFTER_COMMA");
				currentKey = (Number(currentKey) + 1).toString();
				break;
			case "]":
				lastValidIndex = i;
				stack.pop();
				currentKey = path.pop();
				break;
		}
	}
	for (let i = 0; i < input.length; i++) {
		const char = input[i];
		switch (stack[stack.length - 1]) {
			case "ROOT":
				processValueStart(char, i, "FINISH");
				break;
			case "INSIDE_OBJECT_START":
				switch (char) {
					case "\"":
						stack.pop();
						stack.push("INSIDE_OBJECT_KEY");
						currentKey = "";
						break;
					case "}":
						lastValidIndex = i;
						stack.pop();
						currentKey = path.pop();
						break;
				}
				break;
			case "INSIDE_OBJECT_AFTER_COMMA":
				switch (char) {
					case "\"":
						stack.pop();
						stack.push("INSIDE_OBJECT_KEY");
						currentKey = "";
						break;
				}
				break;
			case "INSIDE_OBJECT_KEY":
				switch (char) {
					case "\"":
						stack.pop();
						stack.push("INSIDE_OBJECT_AFTER_KEY");
						break;
					case "\\":
						stack.push("INSIDE_STRING_ESCAPE");
						currentKey += char;
						break;
					default:
						currentKey += char;
						break;
				}
				break;
			case "INSIDE_OBJECT_AFTER_KEY":
				switch (char) {
					case ":":
						stack.pop();
						stack.push("INSIDE_OBJECT_BEFORE_VALUE");
						break;
				}
				break;
			case "INSIDE_OBJECT_BEFORE_VALUE":
				processValueStart(char, i, "INSIDE_OBJECT_AFTER_VALUE");
				break;
			case "INSIDE_OBJECT_AFTER_VALUE":
				processAfterObjectValue(char, i);
				break;
			case "INSIDE_STRING":
				switch (char) {
					case "\"":
						stack.pop();
						lastValidIndex = i;
						currentKey = path.pop();
						break;
					case "\\":
						stack.push("INSIDE_STRING_ESCAPE");
						break;
					default: lastValidIndex = i;
				}
				break;
			case "INSIDE_ARRAY_START":
				switch (char) {
					case "]":
						lastValidIndex = i;
						stack.pop();
						currentKey = path.pop();
						break;
					default:
						lastValidIndex = i;
						currentKey = "0";
						processValueStart(char, i, "INSIDE_ARRAY_AFTER_VALUE");
						break;
				}
				break;
			case "INSIDE_ARRAY_AFTER_VALUE":
				switch (char) {
					case ",":
						stack.pop();
						stack.push("INSIDE_ARRAY_AFTER_COMMA");
						currentKey = (Number(currentKey) + 1).toString();
						break;
					case "]":
						lastValidIndex = i;
						stack.pop();
						currentKey = path.pop();
						break;
					default:
						lastValidIndex = i;
						break;
				}
				break;
			case "INSIDE_ARRAY_AFTER_COMMA":
				processValueStart(char, i, "INSIDE_ARRAY_AFTER_VALUE");
				break;
			case "INSIDE_STRING_ESCAPE":
				stack.pop();
				if (stack[stack.length - 1] === "INSIDE_STRING") lastValidIndex = i;
				else if (stack[stack.length - 1] === "INSIDE_OBJECT_KEY") currentKey += char;
				break;
			case "INSIDE_NUMBER":
				switch (char) {
					case "0":
					case "1":
					case "2":
					case "3":
					case "4":
					case "5":
					case "6":
					case "7":
					case "8":
					case "9":
						lastValidIndex = i;
						break;
					case "e":
					case "E":
					case "-":
					case ".": break;
					case ",":
						stack.pop();
						currentKey = path.pop();
						if (stack[stack.length - 1] === "INSIDE_ARRAY_AFTER_VALUE") processAfterArrayValue(char, i);
						if (stack[stack.length - 1] === "INSIDE_OBJECT_AFTER_VALUE") processAfterObjectValue(char, i);
						break;
					case "}":
						stack.pop();
						currentKey = path.pop();
						if (stack[stack.length - 1] === "INSIDE_OBJECT_AFTER_VALUE") processAfterObjectValue(char, i);
						break;
					case "]":
						stack.pop();
						currentKey = path.pop();
						if (stack[stack.length - 1] === "INSIDE_ARRAY_AFTER_VALUE") processAfterArrayValue(char, i);
						break;
					default:
						stack.pop();
						currentKey = path.pop();
						break;
				}
				break;
			case "INSIDE_LITERAL": {
				const partialLiteral = input.substring(literalStart, i + 1);
				if (!"false".startsWith(partialLiteral) && !"true".startsWith(partialLiteral) && !"null".startsWith(partialLiteral)) {
					stack.pop();
					if (stack[stack.length - 1] === "INSIDE_OBJECT_AFTER_VALUE") processAfterObjectValue(char, i);
					else if (stack[stack.length - 1] === "INSIDE_ARRAY_AFTER_VALUE") processAfterArrayValue(char, i);
				} else lastValidIndex = i;
				break;
			}
		}
	}
	let result = input.slice(0, lastValidIndex + 1);
	for (let i = stack.length - 1; i >= 0; i--) switch (stack[i]) {
		case "INSIDE_STRING":
			result += "\"";
			break;
		case "INSIDE_OBJECT_KEY":
		case "INSIDE_OBJECT_AFTER_KEY":
		case "INSIDE_OBJECT_AFTER_COMMA":
		case "INSIDE_OBJECT_START":
		case "INSIDE_OBJECT_BEFORE_VALUE":
		case "INSIDE_OBJECT_AFTER_VALUE":
			result += "}";
			break;
		case "INSIDE_ARRAY_START":
		case "INSIDE_ARRAY_AFTER_COMMA":
		case "INSIDE_ARRAY_AFTER_VALUE":
			result += "]";
			break;
		case "INSIDE_LITERAL": {
			const partialLiteral = input.substring(literalStart, input.length);
			if ("true".startsWith(partialLiteral)) result += "true".slice(partialLiteral.length);
			else if ("false".startsWith(partialLiteral)) result += "false".slice(partialLiteral.length);
			else if ("null".startsWith(partialLiteral)) result += "null".slice(partialLiteral.length);
		}
	}
	return [result, path];
}
//#endregion
//#region node_modules/assistant-stream/dist/utils/json/parse-partial-json-object.js
var import_secure_json_parse = /* @__PURE__ */ __toESM((/* @__PURE__ */ __commonJSMin(((exports, module) => {
	var hasBuffer = typeof Buffer !== "undefined";
	var suspectProtoRx = /"(?:_|\\u005[Ff])(?:_|\\u005[Ff])(?:p|\\u0070)(?:r|\\u0072)(?:o|\\u006[Ff])(?:t|\\u0074)(?:o|\\u006[Ff])(?:_|\\u005[Ff])(?:_|\\u005[Ff])"\s*:/;
	var suspectConstructorRx = /"(?:c|\\u0063)(?:o|\\u006[Ff])(?:n|\\u006[Ee])(?:s|\\u0073)(?:t|\\u0074)(?:r|\\u0072)(?:u|\\u0075)(?:c|\\u0063)(?:t|\\u0074)(?:o|\\u006[Ff])(?:r|\\u0072)"\s*:/;
	/**
	* @description Internal parse function that parses JSON text with security checks.
	* @private
	* @param {string|Buffer} text - The JSON text string or Buffer to parse.
	* @param {Function} [reviver] - The JSON.parse() optional reviver argument.
	* @param {import('./types').ParseOptions} [options] - Optional configuration object.
	* @returns {*} The parsed object.
	* @throws {SyntaxError} If a forbidden prototype property is found and `options.protoAction` or
	* `options.constructorAction` is `'error'`.
	*/
	function _parse(text, reviver, options) {
		if (options == null) {
			if (reviver !== null && typeof reviver === "object") {
				options = reviver;
				reviver = void 0;
			}
		}
		if (hasBuffer && Buffer.isBuffer(text)) text = text.toString();
		if (text && text.charCodeAt(0) === 65279) text = text.slice(1);
		const obj = JSON.parse(text, reviver);
		if (obj === null || typeof obj !== "object") return obj;
		const protoAction = options && options.protoAction || "error";
		const constructorAction = options && options.constructorAction || "error";
		if (protoAction === "ignore" && constructorAction === "ignore") return obj;
		if (protoAction !== "ignore" && constructorAction !== "ignore") {
			if (suspectProtoRx.test(text) === false && suspectConstructorRx.test(text) === false) return obj;
		} else if (protoAction !== "ignore" && constructorAction === "ignore") {
			if (suspectProtoRx.test(text) === false) return obj;
		} else if (suspectConstructorRx.test(text) === false) return obj;
		return filter(obj, {
			protoAction,
			constructorAction,
			safe: options && options.safe
		});
	}
	/**
	* @description Scans and filters an object for forbidden prototype properties.
	* @param {Object} obj - The object being scanned.
	* @param {import('./types').ParseOptions} [options] - Optional configuration object.
	* @returns {Object|null} The filtered object, or `null` if safe mode is enabled and issues are found.
	* @throws {SyntaxError} If a forbidden prototype property is found and `options.protoAction` or
	* `options.constructorAction` is `'error'`.
	*/
	function filter(obj, { protoAction = "error", constructorAction = "error", safe } = {}) {
		let next = [obj];
		while (next.length) {
			const nodes = next;
			next = [];
			for (const node of nodes) {
				if (protoAction !== "ignore" && Object.prototype.hasOwnProperty.call(node, "__proto__")) {
					if (safe === true) return null;
					else if (protoAction === "error") throw new SyntaxError("Object contains forbidden prototype property");
					delete node.__proto__;
				}
				if (constructorAction !== "ignore" && Object.prototype.hasOwnProperty.call(node, "constructor") && node.constructor !== null && typeof node.constructor === "object" && Object.prototype.hasOwnProperty.call(node.constructor, "prototype")) {
					if (safe === true) return null;
					else if (constructorAction === "error") throw new SyntaxError("Object contains forbidden prototype property");
					delete node.constructor;
				}
				for (const key in node) {
					const value = node[key];
					if (value && typeof value === "object") next.push(value);
				}
			}
		}
		return obj;
	}
	/**
	* @description Parses a given JSON-formatted text into an object.
	* @param {string|Buffer} text - The JSON text string or Buffer to parse.
	* @param {Function} [reviver] - The `JSON.parse()` optional reviver argument, or options object.
	* @param {import('./types').ParseOptions} [options] - Optional configuration object.
	* @returns {*} The parsed object.
	* @throws {SyntaxError} If the JSON text is malformed or contains forbidden prototype properties
	* when `options.protoAction` or `options.constructorAction` is `'error'`.
	*/
	function parse(text, reviver, options) {
		const { stackTraceLimit } = Error;
		Error.stackTraceLimit = 0;
		try {
			return _parse(text, reviver, options);
		} finally {
			Error.stackTraceLimit = stackTraceLimit;
		}
	}
	/**
	* @description Safely parses a given JSON-formatted text into an object.
	* @param {string|Buffer} text - The JSON text string or Buffer to parse.
	* @param {Function} [reviver] - The `JSON.parse()` optional reviver argument.
	* @returns {*|null|undefined} The parsed object, `null` if security issues found, or `undefined` on parse error.
	*/
	function safeParse(text, reviver) {
		const { stackTraceLimit } = Error;
		Error.stackTraceLimit = 0;
		try {
			return _parse(text, reviver, { safe: true });
		} catch {
			return;
		} finally {
			Error.stackTraceLimit = stackTraceLimit;
		}
	}
	module.exports = parse;
	module.exports.default = parse;
	module.exports.parse = parse;
	module.exports.safeParse = safeParse;
	module.exports.scan = filter;
})))(), 1);
var PARTIAL_JSON_OBJECT_META_SYMBOL = Symbol("aui.parse-partial-json-object.meta");
var getPartialJsonObjectMeta = (obj) => {
	return obj?.[PARTIAL_JSON_OBJECT_META_SYMBOL];
};
var parsePartialJsonObject = (json) => {
	if (json.length === 0) return { [PARTIAL_JSON_OBJECT_META_SYMBOL]: {
		state: "partial",
		partialPath: []
	} };
	try {
		const res = import_secure_json_parse.default.parse(json);
		if (typeof res !== "object" || res === null) throw new Error("argsText is expected to be an object");
		res[PARTIAL_JSON_OBJECT_META_SYMBOL] = {
			state: "complete",
			partialPath: []
		};
		return res;
	} catch {
		try {
			const [fixedJson, partialPath] = fixJson(json);
			const res = import_secure_json_parse.default.parse(fixedJson);
			if (typeof res !== "object" || res === null) throw new Error("argsText is expected to be an object");
			res[PARTIAL_JSON_OBJECT_META_SYMBOL] = {
				state: "partial",
				partialPath
			};
			return res;
		} catch {
			return;
		}
	}
};
var getFieldState = (parent, parentMeta, fieldPath) => {
	if (typeof parent !== "object" || parent === null) return parentMeta.state;
	if (parentMeta.state === "complete") return "complete";
	if (fieldPath.length === 0) return parentMeta.state;
	const [field, ...restPath] = fieldPath;
	if (!Object.hasOwn(parent, field)) return "partial";
	const [partialField, ...restPartialPath] = parentMeta.partialPath;
	if (field !== partialField) return "complete";
	const child = parent[field];
	return getFieldState(child, {
		state: "partial",
		partialPath: restPartialPath
	}, restPath);
};
var getPartialJsonObjectFieldState = (obj, fieldPath) => {
	const meta = getPartialJsonObjectMeta(obj);
	if (!meta) throw new Error("unable to determine object state");
	return getFieldState(obj, meta, fieldPath.map(String));
};
//#endregion
//#region node_modules/assistant-stream/dist/utils/AsyncIterableStream.js
async function* streamGeneratorPolyfill() {
	const reader = this.getReader();
	try {
		while (true) {
			const { done, value } = await reader.read();
			if (done) break;
			yield value;
		}
	} finally {
		reader.releaseLock();
	}
}
function asAsyncIterableStream(source) {
	source[Symbol.asyncIterator] ??= streamGeneratorPolyfill;
	return source;
}
//#endregion
//#region node_modules/assistant-stream/dist/core/tool/ToolResponse.js
var TOOL_RESPONSE_SYMBOL = Symbol.for("aui.tool-response");
/**
* Tool result wrapper for separating UI-visible output from model-visible
* output.
*
* Return `ToolResponse` from a tool when you need to attach an artifact, mark
* the result as an error, or control the content sent back to the model.
*
* @example
* ```ts
* return new ToolResponse({
*   result: { title: "Report ready" },
*   artifact: { reportId },
*   modelContent: [{ type: "text", text: "The report is ready." }],
* });
* ```
*/
var ToolResponse = class ToolResponse {
	get [TOOL_RESPONSE_SYMBOL]() {
		return true;
	}
	artifact;
	result;
	isError;
	modelContent;
	messages;
	constructor(options) {
		if (options.artifact !== void 0) this.artifact = options.artifact;
		this.result = options.result;
		this.isError = options.isError ?? false;
		if (options.modelContent !== void 0) this.modelContent = options.modelContent;
		if (options.messages !== void 0) this.messages = options.messages;
	}
	static [Symbol.hasInstance](obj) {
		return typeof obj === "object" && obj !== null && TOOL_RESPONSE_SYMBOL in obj;
	}
	/**
	* Converts a plain tool return value into a {@link ToolResponse}.
	*
	* Existing `ToolResponse` instances are returned unchanged. `undefined`
	* becomes the string `"<no result>"` so downstream protocol chunks always
	* carry a concrete result.
	*/
	static toResponse(result) {
		if (result instanceof ToolResponse) return result;
		return new ToolResponse({ result: result === void 0 ? "<no result>" : result });
	}
};
//#endregion
//#region node_modules/assistant-stream/dist/core/utils/withPromiseOrValue.js
function withPromiseOrValue(callback, thenHandler, catchHandler) {
	try {
		const promiseOrValue = callback();
		if (typeof promiseOrValue === "object" && promiseOrValue !== null && "then" in promiseOrValue) return promiseOrValue.then(thenHandler, catchHandler);
		else thenHandler(promiseOrValue);
	} catch (e) {
		catchHandler(e);
	}
}
//#endregion
//#region node_modules/assistant-stream/dist/core/tool/ToolCallReader.js
function getField(obj, fieldPath) {
	let current = obj;
	for (const key of fieldPath) {
		if (current === void 0 || current === null) return;
		current = current[key];
	}
	return current;
}
var GetHandle = class {
	resolve;
	reject;
	disposed = false;
	fieldPath;
	constructor(resolve, reject, fieldPath) {
		this.resolve = resolve;
		this.reject = reject;
		this.fieldPath = fieldPath;
	}
	update(args) {
		if (this.disposed) return;
		try {
			if (getPartialJsonObjectFieldState(args, this.fieldPath) === "complete") {
				const value = getField(args, this.fieldPath);
				if (value !== void 0) {
					this.resolve(value);
					this.dispose();
				}
			}
		} catch (e) {
			this.reject(e);
			this.dispose();
		}
	}
	end(args) {
		if (this.disposed) return;
		try {
			const value = getField(args, this.fieldPath);
			this.resolve(value);
		} catch (e) {
			this.reject(e);
		} finally {
			this.dispose();
		}
	}
	dispose() {
		this.disposed = true;
	}
};
var StreamValuesHandle = class {
	controller;
	disposed = false;
	fieldPath;
	constructor(controller, fieldPath) {
		this.controller = controller;
		this.fieldPath = fieldPath;
	}
	update(args) {
		if (this.disposed) return;
		try {
			const value = getField(args, this.fieldPath);
			if (value !== void 0) this.controller.enqueue(value);
			if (getPartialJsonObjectFieldState(args, this.fieldPath) === "complete") {
				this.controller.close();
				this.dispose();
			}
		} catch (e) {
			this.controller.error(e);
			this.dispose();
		}
	}
	end() {
		if (this.disposed) return;
		this.controller.close();
		this.dispose();
	}
	dispose() {
		this.disposed = true;
	}
};
var StreamTextHandle = class {
	controller;
	disposed = false;
	fieldPath;
	lastValue = void 0;
	constructor(controller, fieldPath) {
		this.controller = controller;
		this.fieldPath = fieldPath;
	}
	update(args) {
		if (this.disposed) return;
		try {
			const value = getField(args, this.fieldPath);
			if (value !== void 0 && typeof value === "string") {
				const delta = value.substring(this.lastValue?.length || 0);
				this.lastValue = value;
				this.controller.enqueue(delta);
			}
			if (getPartialJsonObjectFieldState(args, this.fieldPath) === "complete") {
				this.controller.close();
				this.dispose();
			}
		} catch (e) {
			this.controller.error(e);
			this.dispose();
		}
	}
	end() {
		if (this.disposed) return;
		this.controller.close();
		this.dispose();
	}
	dispose() {
		this.disposed = true;
	}
};
var ForEachHandle = class {
	controller;
	disposed = false;
	fieldPath;
	processedIndexes = /* @__PURE__ */ new Set();
	constructor(controller, fieldPath) {
		this.controller = controller;
		this.fieldPath = fieldPath;
	}
	update(args) {
		if (this.disposed) return;
		try {
			const array = getField(args, this.fieldPath);
			if (!Array.isArray(array)) return;
			for (let i = 0; i < array.length; i++) if (!this.processedIndexes.has(i)) {
				if (getPartialJsonObjectFieldState(args, [...this.fieldPath, i]) === "complete") {
					this.controller.enqueue(array[i]);
					this.processedIndexes.add(i);
				}
			}
			if (getPartialJsonObjectFieldState(args, this.fieldPath) === "complete") {
				this.controller.close();
				this.dispose();
			}
		} catch (e) {
			this.controller.error(e);
			this.dispose();
		}
	}
	end() {
		if (this.disposed) return;
		this.controller.close();
		this.dispose();
	}
	dispose() {
		this.disposed = true;
	}
};
var ToolCallArgsReaderImpl = class {
	argTextDeltas;
	handles = /* @__PURE__ */ new Set();
	args = parsePartialJsonObject("");
	finished = false;
	constructor(argTextDeltas) {
		this.argTextDeltas = argTextDeltas;
		this.processStream();
	}
	async processStream() {
		try {
			let accumulatedText = "";
			const reader = this.argTextDeltas.getReader();
			while (true) {
				const { value, done } = await reader.read();
				if (done) break;
				accumulatedText += value;
				const parsedArgs = parsePartialJsonObject(accumulatedText);
				if (parsedArgs !== void 0) {
					this.args = parsedArgs;
					for (const handle of this.handles) handle.update(parsedArgs);
				}
			}
		} catch (error) {
			console.error("Error processing argument stream:", error);
		} finally {
			this.finished = true;
			for (const handle of this.handles) handle.end(this.args);
			this.handles.clear();
		}
	}
	get(...fieldPath) {
		return new Promise((resolve, reject) => {
			const handle = new GetHandle(resolve, reject, fieldPath);
			if (this.args && getPartialJsonObjectFieldState(this.args, fieldPath) === "complete") {
				const value = getField(this.args, fieldPath);
				if (value !== void 0) {
					resolve(value);
					return;
				}
			}
			if (this.finished) {
				handle.end(this.args);
				return;
			}
			this.handles.add(handle);
			handle.update(this.args);
		});
	}
	streamValues(...fieldPath) {
		const simplePath = fieldPath;
		let handle;
		return asAsyncIterableStream(new ReadableStream({
			start: (controller) => {
				handle = new StreamValuesHandle(controller, simplePath);
				if (!this.finished) this.handles.add(handle);
				handle.update(this.args);
				if (this.finished) handle.end();
			},
			cancel: () => {
				if (handle) {
					handle.dispose();
					this.handles.delete(handle);
				}
			}
		}));
	}
	streamText(...fieldPath) {
		const simplePath = fieldPath;
		let handle;
		return asAsyncIterableStream(new ReadableStream({
			start: (controller) => {
				handle = new StreamTextHandle(controller, simplePath);
				if (!this.finished) this.handles.add(handle);
				handle.update(this.args);
				if (this.finished) handle.end();
			},
			cancel: () => {
				if (handle) {
					handle.dispose();
					this.handles.delete(handle);
				}
			}
		}));
	}
	forEach(...fieldPath) {
		const simplePath = fieldPath;
		let handle;
		return asAsyncIterableStream(new ReadableStream({
			start: (controller) => {
				handle = new ForEachHandle(controller, simplePath);
				if (!this.finished) this.handles.add(handle);
				handle.update(this.args);
				if (this.finished) handle.end();
			},
			cancel: () => {
				if (handle) {
					handle.dispose();
					this.handles.delete(handle);
				}
			}
		}));
	}
};
var ToolCallResponseReaderImpl = class {
	promise;
	constructor(promise) {
		this.promise = promise;
	}
	get() {
		return this.promise;
	}
};
var ToolCallReaderImpl = class {
	args;
	response;
	writable;
	resolve;
	argsText = "";
	constructor() {
		const stream = new TransformStream();
		this.writable = stream.writable;
		this.args = new ToolCallArgsReaderImpl(stream.readable);
		const { promise, resolve } = promiseWithResolvers();
		this.resolve = resolve;
		this.response = new ToolCallResponseReaderImpl(promise);
	}
	async appendArgsTextDelta(text) {
		const writer = this.writable.getWriter();
		try {
			await writer.write(text);
		} catch (err) {
			console.warn(err);
		} finally {
			writer.releaseLock();
		}
		this.argsText += text;
	}
	async finishArgsText() {
		const writer = this.writable.getWriter();
		try {
			await writer.close();
		} catch (err) {
			console.warn(err);
		} finally {
			writer.releaseLock();
		}
	}
	setResponse(value) {
		this.resolve(value);
	}
	result = { get: async () => {
		return (await this.response.get()).result;
	} };
};
//#endregion
//#region node_modules/assistant-stream/dist/core/tool/ToolExecutionStream.js
var ToolExecutionStream = class extends PipeableTransformStream {
	constructor(options) {
		const toolCallPromises = /* @__PURE__ */ new Map();
		const toolCallControllers = /* @__PURE__ */ new Map();
		super((readable) => {
			const transform = new TransformStream({
				async transform(chunk, controller) {
					if (chunk.type !== "part-finish" || chunk.meta.type !== "tool-call") controller.enqueue(chunk);
					switch (chunk.type) {
						case "part-start":
							if (chunk.part.type === "tool-call") {
								const reader = new ToolCallReaderImpl();
								toolCallControllers.set(chunk.part.toolCallId, reader);
								options.streamCall({
									reader,
									toolCallId: chunk.part.toolCallId,
									toolName: chunk.part.toolName
								});
							}
							break;
						case "text-delta":
							if (chunk.meta.type === "tool-call") {
								const toolCallId = chunk.meta.toolCallId;
								const controller = toolCallControllers.get(toolCallId);
								if (!controller) throw new Error("No controller found for tool call");
								await controller.appendArgsTextDelta(chunk.textDelta);
							}
							break;
						case "result": {
							if (chunk.meta.type !== "tool-call") break;
							const { toolCallId } = chunk.meta;
							const controller = toolCallControllers.get(toolCallId);
							if (!controller) throw new Error("No controller found for tool call");
							controller.setResponse(new ToolResponse({
								result: chunk.result,
								artifact: chunk.artifact,
								isError: chunk.isError,
								modelContent: chunk.modelContent
							}));
							break;
						}
						case "tool-call-args-text-finish": {
							if (chunk.meta.type !== "tool-call") break;
							const { toolCallId, toolName } = chunk.meta;
							const streamController = toolCallControllers.get(toolCallId);
							if (!streamController) throw new Error("No controller found for tool call");
							await streamController.finishArgsText();
							let isExecuting = false;
							const promise = withPromiseOrValue(() => {
								let args;
								try {
									args = import_secure_json_parse.default.parse(streamController.argsText);
								} catch (e) {
									throw new Error(`Function parameter parsing failed. ${JSON.stringify(e.message)}`);
								}
								const executeResult = options.execute({
									toolCallId,
									toolName,
									args
								});
								if (executeResult !== void 0) {
									isExecuting = true;
									options.onExecutionStart?.(toolCallId, toolName);
								}
								return executeResult;
							}, (c) => {
								if (isExecuting) options.onExecutionEnd?.(toolCallId, toolName);
								if (c === void 0) return;
								const result = new ToolResponse({
									artifact: c.artifact,
									result: c.result,
									isError: c.isError,
									messages: c.messages,
									modelContent: c.modelContent
								});
								streamController.setResponse(result);
								controller.enqueue({
									type: "result",
									path: chunk.path,
									...result
								});
							}, (e) => {
								if (isExecuting) options.onExecutionEnd?.(toolCallId, toolName);
								const result = new ToolResponse({
									result: String(e),
									isError: true
								});
								streamController.setResponse(result);
								controller.enqueue({
									type: "result",
									path: chunk.path,
									...result
								});
							});
							if (promise) toolCallPromises.set(toolCallId, promise);
							break;
						}
						case "part-finish": {
							if (chunk.meta.type !== "tool-call") break;
							const { toolCallId } = chunk.meta;
							const toolCallPromise = toolCallPromises.get(toolCallId);
							if (toolCallPromise) toolCallPromise.then(() => {
								toolCallPromises.delete(toolCallId);
								toolCallControllers.delete(toolCallId);
								controller.enqueue(chunk);
							});
							else controller.enqueue(chunk);
						}
					}
				},
				async flush() {
					await Promise.all(toolCallPromises.values());
				}
			});
			return readable.pipeThrough(new AssistantMetaTransformStream()).pipeThrough(transform);
		});
	}
};
//#endregion
//#region node_modules/assistant-stream/dist/core/tool/toolResultStream.js
var isStandardSchemaV1 = (schema) => {
	return typeof schema === "object" && schema !== null && "~standard" in schema && schema["~standard"].version === 1;
};
function getToolResponse(tools, abortSignal, toolCall, human) {
	const tool = tools?.[toolCall.toolName];
	if (!tool?.execute) return void 0;
	const getResult = async (toolExecute) => {
		if (abortSignal.aborted) return new ToolResponse({
			result: "Tool execution was cancelled.",
			isError: true
		});
		let executeFn = toolExecute;
		if (isStandardSchemaV1(tool.parameters)) {
			let result = tool.parameters["~standard"].validate(toolCall.args);
			if (result instanceof Promise) result = await result;
			if (result.issues) executeFn = tool.experimental_onSchemaValidationError ?? (() => {
				throw new Error(`Function parameter validation failed. ${JSON.stringify(result.issues)}`);
			});
		}
		const abortPromise = new Promise((resolve) => {
			const onAbort = () => {
				queueMicrotask(() => {
					queueMicrotask(() => {
						resolve(new ToolResponse({
							result: "Tool execution was cancelled.",
							isError: true
						}));
					});
				});
			};
			if (abortSignal.aborted) onAbort();
			else abortSignal.addEventListener("abort", onAbort, { once: true });
		});
		const executePromise = (async () => {
			const result = await executeFn(toolCall.args, {
				toolCallId: toolCall.toolCallId,
				abortSignal,
				human: (payload) => human(toolCall.toolCallId, payload)
			});
			const response = ToolResponse.toResponse(result);
			if (tool.toModelOutput && !response.isError && response.modelContent === void 0) try {
				const modelContent = await tool.toModelOutput({
					toolCallId: toolCall.toolCallId,
					input: toolCall.args,
					output: response.result
				});
				return new ToolResponse({
					result: response.result,
					artifact: response.artifact,
					isError: response.isError,
					messages: response.messages,
					modelContent
				});
			} catch (e) {
				console.warn(`[assistant-stream] tool "${toolCall.toolName}" toModelOutput threw; falling back to default projection.`, e);
			}
			return response;
		})();
		return Promise.race([executePromise, abortPromise]);
	};
	return getResult(tool.execute);
}
function getToolStreamResponse(tools, abortSignal, reader, context, human) {
	tools?.[context.toolName]?.streamCall?.(reader, {
		toolCallId: context.toolCallId,
		abortSignal,
		human: (payload) => human(context.toolCallId, payload)
	});
}
/**
* Transform stream that executes frontend tools and appends tool results.
*
* The transform watches streamed tool-call arguments, runs the matching
* frontend tool once its arguments are complete, and emits a result chunk for
* the tool call. Backend and human tools pass through according to their tool
* definition.
*
* @param tools Tool registry or function returning the current registry.
* @param abortSignal Signal, or signal getter, used for the current run.
* @param human Callback used to resolve human-tool requests from UI input.
* @param options Optional execution lifecycle callbacks.
*/
function toolResultStream(tools, abortSignal, human, options) {
	const toolsFn = typeof tools === "function" ? tools : () => tools;
	const abortSignalFn = typeof abortSignal === "function" ? abortSignal : () => abortSignal;
	return new ToolExecutionStream({
		execute: (toolCall) => getToolResponse(toolsFn(), abortSignalFn(), toolCall, human),
		streamCall: ({ reader, ...context }) => getToolStreamResponse(toolsFn(), abortSignalFn(), reader, context, human),
		onExecutionStart: options?.onExecutionStart,
		onExecutionEnd: options?.onExecutionEnd
	});
}
//#endregion
export { customAlphabet as a, createAssistantStreamController as i, ToolResponse as n, AssistantMetaTransformStream as o, parsePartialJsonObject as r, toolResultStream as t };
