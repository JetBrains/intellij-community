function isLength(value) {
	return Number.isSafeInteger(value) && value >= 0;
}
function isArrayLike(value) {
	return value != null && typeof value !== "function" && isLength(value.length);
}
function isUnsafeProperty(key) {
	return key === "__proto__";
}
function isPrimitive(value) {
	return value == null || typeof value !== "object" && typeof value !== "function";
}
function getSymbols(object) {
	return Object.getOwnPropertySymbols(object).filter((symbol) => Object.prototype.propertyIsEnumerable.call(object, symbol));
}
function getTag(value) {
	if (value == null) return value === void 0 ? "[object Undefined]" : "[object Null]";
	return Object.prototype.toString.call(value);
}
var regexpTag = "[object RegExp]";
var stringTag = "[object String]";
var numberTag = "[object Number]";
var booleanTag = "[object Boolean]";
var argumentsTag = "[object Arguments]";
var symbolTag = "[object Symbol]";
var dateTag = "[object Date]";
var mapTag = "[object Map]";
var setTag = "[object Set]";
var arrayTag = "[object Array]";
var arrayBufferTag = "[object ArrayBuffer]";
var objectTag = "[object Object]";
var dataViewTag = "[object DataView]";
var uint8ArrayTag = "[object Uint8Array]";
var uint8ClampedArrayTag = "[object Uint8ClampedArray]";
var uint16ArrayTag = "[object Uint16Array]";
var uint32ArrayTag = "[object Uint32Array]";
var int8ArrayTag = "[object Int8Array]";
var int16ArrayTag = "[object Int16Array]";
var int32ArrayTag = "[object Int32Array]";
var float32ArrayTag = "[object Float32Array]";
var float64ArrayTag = "[object Float64Array]";
var globalThis_ = typeof globalThis === "object" && globalThis || typeof window === "object" && window || typeof self === "object" && self || typeof global === "object" && global || (function() {
	return this;
})() || Function("return this")();
function isBuffer(x) {
	return typeof globalThis_.Buffer !== "undefined" && globalThis_.Buffer.isBuffer(x);
}
function isTypedArray$1(x) {
	return ArrayBuffer.isView(x) && !(x instanceof DataView);
}
function cloneDeepWith$1(obj, cloneValue) {
	return cloneDeepWithImpl(obj, void 0, obj, /* @__PURE__ */ new Map(), cloneValue);
}
function cloneDeepWithImpl(valueToClone, keyToClone, objectToClone, stack = /* @__PURE__ */ new Map(), cloneValue = void 0) {
	const cloned = cloneValue?.(valueToClone, keyToClone, objectToClone, stack);
	if (cloned !== void 0) return cloned;
	if (isPrimitive(valueToClone)) return valueToClone;
	if (stack.has(valueToClone)) return stack.get(valueToClone);
	if (Array.isArray(valueToClone)) {
		const result = new Array(valueToClone.length);
		stack.set(valueToClone, result);
		for (let i = 0; i < valueToClone.length; i++) result[i] = cloneDeepWithImpl(valueToClone[i], i, objectToClone, stack, cloneValue);
		if (Object.hasOwn(valueToClone, "index")) result.index = valueToClone.index;
		if (Object.hasOwn(valueToClone, "input")) result.input = valueToClone.input;
		return result;
	}
	if (valueToClone instanceof Date) return new Date(valueToClone.getTime());
	if (valueToClone instanceof RegExp) {
		const result = new RegExp(valueToClone.source, valueToClone.flags);
		result.lastIndex = valueToClone.lastIndex;
		return result;
	}
	if (valueToClone instanceof Map) {
		const result = /* @__PURE__ */ new Map();
		stack.set(valueToClone, result);
		for (const [key, value] of valueToClone) result.set(key, cloneDeepWithImpl(value, key, objectToClone, stack, cloneValue));
		return result;
	}
	if (valueToClone instanceof Set) {
		const result = /* @__PURE__ */ new Set();
		stack.set(valueToClone, result);
		for (const value of valueToClone) result.add(cloneDeepWithImpl(value, void 0, objectToClone, stack, cloneValue));
		return result;
	}
	if (isBuffer(valueToClone)) return valueToClone.subarray();
	if (isTypedArray$1(valueToClone)) {
		const result = new (Object.getPrototypeOf(valueToClone)).constructor(valueToClone.length);
		stack.set(valueToClone, result);
		for (let i = 0; i < valueToClone.length; i++) result[i] = cloneDeepWithImpl(valueToClone[i], i, objectToClone, stack, cloneValue);
		return result;
	}
	if (valueToClone instanceof ArrayBuffer || typeof SharedArrayBuffer !== "undefined" && valueToClone instanceof SharedArrayBuffer) return valueToClone.slice(0);
	if (valueToClone instanceof DataView) {
		const result = new DataView(valueToClone.buffer.slice(0), valueToClone.byteOffset, valueToClone.byteLength);
		stack.set(valueToClone, result);
		copyProperties(result, valueToClone, objectToClone, stack, cloneValue);
		return result;
	}
	if (typeof File !== "undefined" && valueToClone instanceof File) {
		const result = new File([valueToClone], valueToClone.name, { type: valueToClone.type });
		stack.set(valueToClone, result);
		copyProperties(result, valueToClone, objectToClone, stack, cloneValue);
		return result;
	}
	if (typeof Blob !== "undefined" && valueToClone instanceof Blob) {
		const result = new Blob([valueToClone], { type: valueToClone.type });
		stack.set(valueToClone, result);
		copyProperties(result, valueToClone, objectToClone, stack, cloneValue);
		return result;
	}
	if (valueToClone instanceof Error) {
		const result = structuredClone(valueToClone);
		stack.set(valueToClone, result);
		result.message = valueToClone.message;
		result.name = valueToClone.name;
		result.stack = valueToClone.stack;
		result.cause = valueToClone.cause;
		result.constructor = valueToClone.constructor;
		copyProperties(result, valueToClone, objectToClone, stack, cloneValue);
		return result;
	}
	if (valueToClone instanceof Boolean) {
		const result = new Boolean(valueToClone.valueOf());
		stack.set(valueToClone, result);
		copyProperties(result, valueToClone, objectToClone, stack, cloneValue);
		return result;
	}
	if (valueToClone instanceof Number) {
		const result = new Number(valueToClone.valueOf());
		stack.set(valueToClone, result);
		copyProperties(result, valueToClone, objectToClone, stack, cloneValue);
		return result;
	}
	if (valueToClone instanceof String) {
		const result = new String(valueToClone.valueOf());
		stack.set(valueToClone, result);
		copyProperties(result, valueToClone, objectToClone, stack, cloneValue);
		return result;
	}
	if (typeof valueToClone === "object" && isCloneableObject$1(valueToClone)) {
		const result = Object.create(Object.getPrototypeOf(valueToClone));
		stack.set(valueToClone, result);
		copyProperties(result, valueToClone, objectToClone, stack, cloneValue);
		return result;
	}
	return valueToClone;
}
function copyProperties(target, source, objectToClone = target, stack, cloneValue) {
	const keys = [...Object.keys(source), ...getSymbols(source)];
	for (let i = 0; i < keys.length; i++) {
		const key = keys[i];
		const descriptor = Object.getOwnPropertyDescriptor(target, key);
		if (descriptor == null || descriptor.writable) target[key] = cloneDeepWithImpl(source[key], key, objectToClone, stack, cloneValue);
	}
}
function isCloneableObject$1(object) {
	switch (getTag(object)) {
		case argumentsTag:
		case arrayTag:
		case arrayBufferTag:
		case dataViewTag:
		case booleanTag:
		case dateTag:
		case float32ArrayTag:
		case float64ArrayTag:
		case int8ArrayTag:
		case int16ArrayTag:
		case int32ArrayTag:
		case mapTag:
		case numberTag:
		case objectTag:
		case regexpTag:
		case setTag:
		case stringTag:
		case symbolTag:
		case uint8ArrayTag:
		case uint8ClampedArrayTag:
		case uint16ArrayTag:
		case uint32ArrayTag: return true;
		default: return false;
	}
}
function cloneDeepWith(obj, customizer) {
	return cloneDeepWith$1(obj, (value, key, object, stack) => {
		const cloned = customizer?.(value, key, object, stack);
		if (cloned !== void 0) return cloned;
		if (typeof obj !== "object") return;
		if (getTag(obj) === "[object Object]" && typeof obj.constructor !== "function") {
			const result = {};
			stack.set(obj, result);
			copyProperties(result, obj, object, stack);
			return result;
		}
		switch (Object.prototype.toString.call(obj)) {
			case numberTag:
			case stringTag:
			case booleanTag: {
				const result = new obj.constructor(obj?.valueOf());
				copyProperties(result, obj);
				return result;
			}
			case argumentsTag: {
				const result = {};
				copyProperties(result, obj);
				result.length = obj.length;
				result[Symbol.iterator] = obj[Symbol.iterator];
				return result;
			}
			default: return;
		}
	});
}
function cloneDeep(obj) {
	return cloneDeepWith(obj);
}
function isArguments(value) {
	return value !== null && typeof value === "object" && getTag(value) === "[object Arguments]";
}
function isObjectLike(value) {
	return typeof value === "object" && value !== null;
}
function isArrayLikeObject(value) {
	return isObjectLike(value) && isArrayLike(value);
}
function isArray(value) {
	return Array.isArray(value);
}
function memoize(func, resolver) {
	if (typeof func !== "function" || resolver != null && typeof resolver !== "function") throw new TypeError("Expected a function");
	const memoized = function(...args) {
		const key = resolver ? resolver.apply(this, args) : args[0];
		const cache = memoized.cache;
		if (cache.has(key)) return cache.get(key);
		const result = func.apply(this, args);
		memoized.cache = cache.set(key, result) || cache;
		return result;
	};
	memoized.cache = new (memoize.Cache || Map)();
	return memoized;
}
memoize.Cache = Map;
function noop() {}
function isPrototype(value) {
	const constructor = value?.constructor;
	return value === (typeof constructor === "function" ? constructor.prototype : Object.prototype);
}
function isTypedArray(x) {
	return isTypedArray$1(x);
}
function clone$1(obj) {
	if (isPrimitive(obj)) return obj;
	const tag = getTag(obj);
	if (!isCloneableObject(obj)) return {};
	if (isArray(obj)) {
		const result = Array.from(obj);
		if (obj.length > 0 && typeof obj[0] === "string" && Object.hasOwn(obj, "index")) {
			result.index = obj.index;
			result.input = obj.input;
		}
		return result;
	}
	if (isTypedArray(obj)) {
		const typedArray = obj;
		const Ctor = typedArray.constructor;
		return new Ctor(typedArray.buffer, typedArray.byteOffset, typedArray.length);
	}
	if (tag === "[object ArrayBuffer]") return new ArrayBuffer(obj.byteLength);
	if (tag === "[object DataView]") {
		const dataView = obj;
		const buffer = dataView.buffer;
		const byteOffset = dataView.byteOffset;
		const byteLength = dataView.byteLength;
		const clonedBuffer = new ArrayBuffer(byteLength);
		const srcView = new Uint8Array(buffer, byteOffset, byteLength);
		new Uint8Array(clonedBuffer).set(srcView);
		return new DataView(clonedBuffer);
	}
	if (tag === "[object Boolean]" || tag === "[object Number]" || tag === "[object String]") {
		const Ctor = obj.constructor;
		const clone = new Ctor(obj.valueOf());
		if (tag === "[object String]") cloneStringObjectProperties(clone, obj);
		else copyOwnProperties(clone, obj);
		return clone;
	}
	if (tag === "[object Date]") return new Date(Number(obj));
	if (tag === "[object RegExp]") {
		const regExp = obj;
		const clone = new RegExp(regExp.source, regExp.flags);
		clone.lastIndex = regExp.lastIndex;
		return clone;
	}
	if (tag === "[object Symbol]") return Object(Symbol.prototype.valueOf.call(obj));
	if (tag === "[object Map]") {
		const map = obj;
		const result = /* @__PURE__ */ new Map();
		map.forEach((obj, key) => {
			result.set(key, obj);
		});
		return result;
	}
	if (tag === "[object Set]") {
		const set = obj;
		const result = /* @__PURE__ */ new Set();
		set.forEach((obj) => {
			result.add(obj);
		});
		return result;
	}
	if (tag === "[object Arguments]") {
		const args = obj;
		const result = {};
		copyOwnProperties(result, args);
		result.length = args.length;
		result[Symbol.iterator] = args[Symbol.iterator];
		return result;
	}
	const result = {};
	copyPrototype(result, obj);
	copyOwnProperties(result, obj);
	copySymbolProperties(result, obj);
	return result;
}
function isCloneableObject(object) {
	switch (getTag(object)) {
		case argumentsTag:
		case arrayTag:
		case arrayBufferTag:
		case dataViewTag:
		case booleanTag:
		case dateTag:
		case float32ArrayTag:
		case float64ArrayTag:
		case int8ArrayTag:
		case int16ArrayTag:
		case int32ArrayTag:
		case mapTag:
		case numberTag:
		case objectTag:
		case regexpTag:
		case setTag:
		case stringTag:
		case symbolTag:
		case uint8ArrayTag:
		case uint8ClampedArrayTag:
		case uint16ArrayTag:
		case uint32ArrayTag: return true;
		default: return false;
	}
}
function copyOwnProperties(target, source) {
	for (const key in source) if (Object.hasOwn(source, key)) target[key] = source[key];
}
function copySymbolProperties(target, source) {
	const symbols = Object.getOwnPropertySymbols(source);
	for (let i = 0; i < symbols.length; i++) {
		const symbol = symbols[i];
		if (Object.prototype.propertyIsEnumerable.call(source, symbol)) target[symbol] = source[symbol];
	}
}
function cloneStringObjectProperties(target, source) {
	const stringLength = source.valueOf().length;
	for (const key in source) if (Object.hasOwn(source, key) && (Number.isNaN(Number(key)) || Number(key) >= stringLength)) target[key] = source[key];
}
function copyPrototype(target, source) {
	const proto = Object.getPrototypeOf(source);
	if (proto !== null) {
		if (typeof source.constructor === "function") Object.setPrototypeOf(target, proto);
	}
}
function isPlainObject(object) {
	if (typeof object !== "object") return false;
	if (object == null) return false;
	if (Object.getPrototypeOf(object) === null) return true;
	if (Object.prototype.toString.call(object) !== "[object Object]") {
		const tag = object[Symbol.toStringTag];
		if (tag == null) return false;
		if (!Object.getOwnPropertyDescriptor(object, Symbol.toStringTag)?.writable) return false;
		return object.toString() === `[object ${tag}]`;
	}
	let proto = object;
	while (Object.getPrototypeOf(proto) !== null) proto = Object.getPrototypeOf(proto);
	return Object.getPrototypeOf(object) === proto;
}
function clone(obj) {
	if (isPrimitive(obj)) return obj;
	if (Array.isArray(obj) || isTypedArray$1(obj) || obj instanceof ArrayBuffer || typeof SharedArrayBuffer !== "undefined" && obj instanceof SharedArrayBuffer) return obj.slice(0);
	const prototype = Object.getPrototypeOf(obj);
	if (prototype == null) return Object.assign(Object.create(prototype), obj);
	const Constructor = prototype.constructor;
	if (obj instanceof Date || obj instanceof Map || obj instanceof Set) return new Constructor(obj);
	if (obj instanceof RegExp) {
		const newRegExp = new Constructor(obj);
		newRegExp.lastIndex = obj.lastIndex;
		return newRegExp;
	}
	if (obj instanceof DataView) return new Constructor(obj.buffer.slice(0));
	if (obj instanceof Error) {
		let newError;
		if (obj instanceof AggregateError) newError = new Constructor(obj.errors, obj.message, { cause: obj.cause });
		else newError = new Constructor(obj.message, { cause: obj.cause });
		newError.stack = obj.stack;
		Object.assign(newError, obj);
		return newError;
	}
	if (typeof File !== "undefined" && obj instanceof File) return new Constructor([obj], obj.name, {
		type: obj.type,
		lastModified: obj.lastModified
	});
	if (typeof obj === "object") return Object.assign(Object.create(prototype), obj);
	return obj;
}
function mergeWith(object, ...otherArgs) {
	const sources = otherArgs.slice(0, -1);
	const merge = otherArgs[otherArgs.length - 1];
	let result = object;
	for (let i = 0; i < sources.length; i++) {
		const source = sources[i];
		result = mergeWithDeep(result, source, merge, /* @__PURE__ */ new Map());
	}
	return result;
}
function mergeWithDeep(target, source, merge, stack) {
	if (isPrimitive(target)) target = Object(target);
	if (source == null || typeof source !== "object") return target;
	if (stack.has(source)) return clone(stack.get(source));
	stack.set(source, target);
	if (Array.isArray(source)) {
		source = source.slice();
		for (let i = 0; i < source.length; i++) source[i] = source[i] ?? void 0;
	}
	const sourceKeys = [...Object.keys(source), ...getSymbols(source)];
	for (let i = 0; i < sourceKeys.length; i++) {
		const key = sourceKeys[i];
		if (isUnsafeProperty(key)) continue;
		let sourceValue = source[key];
		let targetValue = target[key];
		if (isArguments(sourceValue)) sourceValue = { ...sourceValue };
		if (isArguments(targetValue)) targetValue = { ...targetValue };
		if (isBuffer(sourceValue)) sourceValue = cloneDeep(sourceValue);
		if (Array.isArray(sourceValue)) if (Array.isArray(targetValue)) {
			const cloned = [];
			const targetKeys = Reflect.ownKeys(targetValue);
			for (let i = 0; i < targetKeys.length; i++) {
				const targetKey = targetKeys[i];
				cloned[targetKey] = targetValue[targetKey];
			}
			targetValue = cloned;
		} else if (isArrayLikeObject(targetValue)) {
			const cloned = [];
			for (let i = 0; i < targetValue.length; i++) cloned[i] = targetValue[i];
			targetValue = cloned;
		} else targetValue = [];
		const merged = merge(targetValue, sourceValue, key, target, source, stack);
		if (merged !== void 0) target[key] = merged;
		else if (Array.isArray(sourceValue)) target[key] = mergeWithDeep(targetValue, sourceValue, merge, stack);
		else if (isObjectLike(targetValue) && isObjectLike(sourceValue) && (isPlainObject(targetValue) || isPlainObject(sourceValue) || isTypedArray(targetValue) || isTypedArray(sourceValue))) target[key] = mergeWithDeep(targetValue, sourceValue, merge, stack);
		else if (targetValue == null && isPlainObject(sourceValue)) target[key] = mergeWithDeep({}, sourceValue, merge, stack);
		else if (targetValue == null && isTypedArray(sourceValue)) target[key] = cloneDeep(sourceValue);
		else if (targetValue === void 0 || sourceValue !== void 0) target[key] = sourceValue;
	}
	return target;
}
function merge(object, ...sources) {
	return mergeWith(object, ...sources, noop);
}
function isEmpty(value) {
	if (value == null) return true;
	if (isArrayLike(value)) {
		if (typeof value.splice !== "function" && typeof value !== "string" && !isBuffer(value) && !isTypedArray(value) && !isArguments(value)) return false;
		return value.length === 0;
	}
	if (typeof value === "object") {
		if (value instanceof Map || value instanceof Set) return value.size === 0;
		const keys = Object.keys(value);
		if (isPrototype(value)) return keys.filter((x) => x !== "constructor").length === 0;
		return keys.length === 0;
	}
	return true;
}
export { memoize as i, merge as n, clone$1 as r, isEmpty as t };
