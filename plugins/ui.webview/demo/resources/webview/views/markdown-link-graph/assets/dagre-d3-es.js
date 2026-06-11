import { $ as eq, A as baseGetAllKeys, B as nodeUtil, C as castPath, D as keys, E as getAllKeys, F as baseKeys, G as isArguments, H as isBuffer, I as identity, J as overArg, K as isObjectLike, L as arrayLikeKeys, M as arrayMap, N as isEmpty, O as getSymbols, P as _getTag_default, R as isIndex, S as toKey, T as isSymbol, U as isArrayLike, V as baseUnary, W as isArray, X as baseFor, Y as Uint8Array, Z as Stack, _ as baseIteratee, a as arrayEach, at as root, b as hasPath, c as baseFindIndex, d as baseLt, et as getNative, f as baseExtremum, g as baseForOwn, h as baseMap, i as castFunction, it as Symbol, j as arrayPush, k as stubArray, l as baseFlatten, m as map, n as reduce, nt as isObject, o as flatten, p as filter, q as isPrototype, r as forEach, rt as baseGetTag, s as baseUniq, tt as isFunction, u as min, v as baseProperty, w as toString, x as baseGet, y as hasIn, z as isTypedArray } from "./chevrotain-allstar.js";
//#region node_modules/lodash-es/_defineProperty.js
var defineProperty = function() {
	try {
		var func = getNative(Object, "defineProperty");
		func({}, "", {});
		return func;
	} catch (e) {}
}();
//#endregion
//#region node_modules/lodash-es/_baseAssignValue.js
/**
* The base implementation of `assignValue` and `assignMergeValue` without
* value checks.
*
* @private
* @param {Object} object The object to modify.
* @param {string} key The key of the property to assign.
* @param {*} value The value to assign.
*/
function baseAssignValue(object, key, value) {
	if (key == "__proto__" && defineProperty) defineProperty(object, key, {
		"configurable": true,
		"enumerable": true,
		"value": value,
		"writable": true
	});
	else object[key] = value;
}
//#endregion
//#region node_modules/lodash-es/_assignMergeValue.js
/**
* This function is like `assignValue` except that it doesn't assign
* `undefined` values.
*
* @private
* @param {Object} object The object to modify.
* @param {string} key The key of the property to assign.
* @param {*} value The value to assign.
*/
function assignMergeValue(object, key, value) {
	if (value !== void 0 && !eq(object[key], value) || value === void 0 && !(key in object)) baseAssignValue(object, key, value);
}
//#endregion
//#region node_modules/lodash-es/_cloneBuffer.js
/** Detect free variable `exports`. */
var freeExports = typeof exports == "object" && exports && !exports.nodeType && exports;
/** Detect free variable `module`. */
var freeModule = freeExports && typeof module == "object" && module && !module.nodeType && module;
/** Built-in value references. */
var Buffer = freeModule && freeModule.exports === freeExports ? root.Buffer : void 0, allocUnsafe = Buffer ? Buffer.allocUnsafe : void 0;
/**
* Creates a clone of  `buffer`.
*
* @private
* @param {Buffer} buffer The buffer to clone.
* @param {boolean} [isDeep] Specify a deep clone.
* @returns {Buffer} Returns the cloned buffer.
*/
function cloneBuffer(buffer, isDeep) {
	if (isDeep) return buffer.slice();
	var length = buffer.length, result = allocUnsafe ? allocUnsafe(length) : new buffer.constructor(length);
	buffer.copy(result);
	return result;
}
//#endregion
//#region node_modules/lodash-es/_cloneArrayBuffer.js
/**
* Creates a clone of `arrayBuffer`.
*
* @private
* @param {ArrayBuffer} arrayBuffer The array buffer to clone.
* @returns {ArrayBuffer} Returns the cloned array buffer.
*/
function cloneArrayBuffer(arrayBuffer) {
	var result = new arrayBuffer.constructor(arrayBuffer.byteLength);
	new Uint8Array(result).set(new Uint8Array(arrayBuffer));
	return result;
}
//#endregion
//#region node_modules/lodash-es/_cloneTypedArray.js
/**
* Creates a clone of `typedArray`.
*
* @private
* @param {Object} typedArray The typed array to clone.
* @param {boolean} [isDeep] Specify a deep clone.
* @returns {Object} Returns the cloned typed array.
*/
function cloneTypedArray(typedArray, isDeep) {
	var buffer = isDeep ? cloneArrayBuffer(typedArray.buffer) : typedArray.buffer;
	return new typedArray.constructor(buffer, typedArray.byteOffset, typedArray.length);
}
//#endregion
//#region node_modules/lodash-es/_copyArray.js
/**
* Copies the values of `source` to `array`.
*
* @private
* @param {Array} source The array to copy values from.
* @param {Array} [array=[]] The array to copy values to.
* @returns {Array} Returns `array`.
*/
function copyArray(source, array) {
	var index = -1, length = source.length;
	array || (array = Array(length));
	while (++index < length) array[index] = source[index];
	return array;
}
//#endregion
//#region node_modules/lodash-es/_baseCreate.js
/** Built-in value references. */
var objectCreate = Object.create;
/**
* The base implementation of `_.create` without support for assigning
* properties to the created object.
*
* @private
* @param {Object} proto The object to inherit from.
* @returns {Object} Returns the new object.
*/
var baseCreate = function() {
	function object() {}
	return function(proto) {
		if (!isObject(proto)) return {};
		if (objectCreate) return objectCreate(proto);
		object.prototype = proto;
		var result = new object();
		object.prototype = void 0;
		return result;
	};
}();
//#endregion
//#region node_modules/lodash-es/_getPrototype.js
/** Built-in value references. */
var getPrototype = overArg(Object.getPrototypeOf, Object);
//#endregion
//#region node_modules/lodash-es/_initCloneObject.js
/**
* Initializes an object clone.
*
* @private
* @param {Object} object The object to clone.
* @returns {Object} Returns the initialized clone.
*/
function initCloneObject(object) {
	return typeof object.constructor == "function" && !isPrototype(object) ? baseCreate(getPrototype(object)) : {};
}
//#endregion
//#region node_modules/lodash-es/isArrayLikeObject.js
/**
* This method is like `_.isArrayLike` except that it also checks if `value`
* is an object.
*
* @static
* @memberOf _
* @since 4.0.0
* @category Lang
* @param {*} value The value to check.
* @returns {boolean} Returns `true` if `value` is an array-like object,
*  else `false`.
* @example
*
* _.isArrayLikeObject([1, 2, 3]);
* // => true
*
* _.isArrayLikeObject(document.body.children);
* // => true
*
* _.isArrayLikeObject('abc');
* // => false
*
* _.isArrayLikeObject(_.noop);
* // => false
*/
function isArrayLikeObject(value) {
	return isObjectLike(value) && isArrayLike(value);
}
//#endregion
//#region node_modules/lodash-es/isPlainObject.js
/** `Object#toString` result references. */
var objectTag$1 = "[object Object]";
/** Used for built-in method references. */
var funcProto = Function.prototype, objectProto$1 = Object.prototype;
/** Used to resolve the decompiled source of functions. */
var funcToString = funcProto.toString;
/** Used to check objects for own properties. */
var hasOwnProperty$5 = objectProto$1.hasOwnProperty;
/** Used to infer the `Object` constructor. */
var objectCtorString = funcToString.call(Object);
/**
* Checks if `value` is a plain object, that is, an object created by the
* `Object` constructor or one with a `[[Prototype]]` of `null`.
*
* @static
* @memberOf _
* @since 0.8.0
* @category Lang
* @param {*} value The value to check.
* @returns {boolean} Returns `true` if `value` is a plain object, else `false`.
* @example
*
* function Foo() {
*   this.a = 1;
* }
*
* _.isPlainObject(new Foo);
* // => false
*
* _.isPlainObject([1, 2, 3]);
* // => false
*
* _.isPlainObject({ 'x': 0, 'y': 0 });
* // => true
*
* _.isPlainObject(Object.create(null));
* // => true
*/
function isPlainObject(value) {
	if (!isObjectLike(value) || baseGetTag(value) != objectTag$1) return false;
	var proto = getPrototype(value);
	if (proto === null) return true;
	var Ctor = hasOwnProperty$5.call(proto, "constructor") && proto.constructor;
	return typeof Ctor == "function" && Ctor instanceof Ctor && funcToString.call(Ctor) == objectCtorString;
}
//#endregion
//#region node_modules/lodash-es/_safeGet.js
/**
* Gets the value at `key`, unless `key` is "__proto__" or "constructor".
*
* @private
* @param {Object} object The object to query.
* @param {string} key The key of the property to get.
* @returns {*} Returns the property value.
*/
function safeGet(object, key) {
	if (key === "constructor" && typeof object[key] === "function") return;
	if (key == "__proto__") return;
	return object[key];
}
//#endregion
//#region node_modules/lodash-es/_assignValue.js
/** Used to check objects for own properties. */
var hasOwnProperty$4 = Object.prototype.hasOwnProperty;
/**
* Assigns `value` to `key` of `object` if the existing value is not equivalent
* using [`SameValueZero`](http://ecma-international.org/ecma-262/7.0/#sec-samevaluezero)
* for equality comparisons.
*
* @private
* @param {Object} object The object to modify.
* @param {string} key The key of the property to assign.
* @param {*} value The value to assign.
*/
function assignValue(object, key, value) {
	var objValue = object[key];
	if (!(hasOwnProperty$4.call(object, key) && eq(objValue, value)) || value === void 0 && !(key in object)) baseAssignValue(object, key, value);
}
//#endregion
//#region node_modules/lodash-es/_copyObject.js
/**
* Copies properties of `source` to `object`.
*
* @private
* @param {Object} source The object to copy properties from.
* @param {Array} props The property identifiers to copy.
* @param {Object} [object={}] The object to copy properties to.
* @param {Function} [customizer] The function to customize copied values.
* @returns {Object} Returns `object`.
*/
function copyObject(source, props, object, customizer) {
	var isNew = !object;
	object || (object = {});
	var index = -1, length = props.length;
	while (++index < length) {
		var key = props[index];
		var newValue = customizer ? customizer(object[key], source[key], key, object, source) : void 0;
		if (newValue === void 0) newValue = source[key];
		if (isNew) baseAssignValue(object, key, newValue);
		else assignValue(object, key, newValue);
	}
	return object;
}
//#endregion
//#region node_modules/lodash-es/_nativeKeysIn.js
/**
* This function is like
* [`Object.keys`](http://ecma-international.org/ecma-262/7.0/#sec-object.keys)
* except that it includes inherited enumerable properties.
*
* @private
* @param {Object} object The object to query.
* @returns {Array} Returns the array of property names.
*/
function nativeKeysIn(object) {
	var result = [];
	if (object != null) for (var key in Object(object)) result.push(key);
	return result;
}
//#endregion
//#region node_modules/lodash-es/_baseKeysIn.js
/** Used to check objects for own properties. */
var hasOwnProperty$3 = Object.prototype.hasOwnProperty;
/**
* The base implementation of `_.keysIn` which doesn't treat sparse arrays as dense.
*
* @private
* @param {Object} object The object to query.
* @returns {Array} Returns the array of property names.
*/
function baseKeysIn(object) {
	if (!isObject(object)) return nativeKeysIn(object);
	var isProto = isPrototype(object), result = [];
	for (var key in object) if (!(key == "constructor" && (isProto || !hasOwnProperty$3.call(object, key)))) result.push(key);
	return result;
}
//#endregion
//#region node_modules/lodash-es/keysIn.js
/**
* Creates an array of the own and inherited enumerable property names of `object`.
*
* **Note:** Non-object values are coerced to objects.
*
* @static
* @memberOf _
* @since 3.0.0
* @category Object
* @param {Object} object The object to query.
* @returns {Array} Returns the array of property names.
* @example
*
* function Foo() {
*   this.a = 1;
*   this.b = 2;
* }
*
* Foo.prototype.c = 3;
*
* _.keysIn(new Foo);
* // => ['a', 'b', 'c'] (iteration order is not guaranteed)
*/
function keysIn(object) {
	return isArrayLike(object) ? arrayLikeKeys(object, true) : baseKeysIn(object);
}
//#endregion
//#region node_modules/lodash-es/toPlainObject.js
/**
* Converts `value` to a plain object flattening inherited enumerable string
* keyed properties of `value` to own properties of the plain object.
*
* @static
* @memberOf _
* @since 3.0.0
* @category Lang
* @param {*} value The value to convert.
* @returns {Object} Returns the converted plain object.
* @example
*
* function Foo() {
*   this.b = 2;
* }
*
* Foo.prototype.c = 3;
*
* _.assign({ 'a': 1 }, new Foo);
* // => { 'a': 1, 'b': 2 }
*
* _.assign({ 'a': 1 }, _.toPlainObject(new Foo));
* // => { 'a': 1, 'b': 2, 'c': 3 }
*/
function toPlainObject(value) {
	return copyObject(value, keysIn(value));
}
//#endregion
//#region node_modules/lodash-es/_baseMergeDeep.js
/**
* A specialized version of `baseMerge` for arrays and objects which performs
* deep merges and tracks traversed objects enabling objects with circular
* references to be merged.
*
* @private
* @param {Object} object The destination object.
* @param {Object} source The source object.
* @param {string} key The key of the value to merge.
* @param {number} srcIndex The index of `source`.
* @param {Function} mergeFunc The function to merge values.
* @param {Function} [customizer] The function to customize assigned values.
* @param {Object} [stack] Tracks traversed source values and their merged
*  counterparts.
*/
function baseMergeDeep(object, source, key, srcIndex, mergeFunc, customizer, stack) {
	var objValue = safeGet(object, key), srcValue = safeGet(source, key), stacked = stack.get(srcValue);
	if (stacked) {
		assignMergeValue(object, key, stacked);
		return;
	}
	var newValue = customizer ? customizer(objValue, srcValue, key + "", object, source, stack) : void 0;
	var isCommon = newValue === void 0;
	if (isCommon) {
		var isArr = isArray(srcValue), isBuff = !isArr && isBuffer(srcValue), isTyped = !isArr && !isBuff && isTypedArray(srcValue);
		newValue = srcValue;
		if (isArr || isBuff || isTyped) if (isArray(objValue)) newValue = objValue;
		else if (isArrayLikeObject(objValue)) newValue = copyArray(objValue);
		else if (isBuff) {
			isCommon = false;
			newValue = cloneBuffer(srcValue, true);
		} else if (isTyped) {
			isCommon = false;
			newValue = cloneTypedArray(srcValue, true);
		} else newValue = [];
		else if (isPlainObject(srcValue) || isArguments(srcValue)) {
			newValue = objValue;
			if (isArguments(objValue)) newValue = toPlainObject(objValue);
			else if (!isObject(objValue) || isFunction(objValue)) newValue = initCloneObject(srcValue);
		} else isCommon = false;
	}
	if (isCommon) {
		stack.set(srcValue, newValue);
		mergeFunc(newValue, srcValue, srcIndex, customizer, stack);
		stack["delete"](srcValue);
	}
	assignMergeValue(object, key, newValue);
}
//#endregion
//#region node_modules/lodash-es/_baseMerge.js
/**
* The base implementation of `_.merge` without support for multiple sources.
*
* @private
* @param {Object} object The destination object.
* @param {Object} source The source object.
* @param {number} srcIndex The index of `source`.
* @param {Function} [customizer] The function to customize merged values.
* @param {Object} [stack] Tracks traversed source values and their merged
*  counterparts.
*/
function baseMerge(object, source, srcIndex, customizer, stack) {
	if (object === source) return;
	baseFor(source, function(srcValue, key) {
		stack || (stack = new Stack());
		if (isObject(srcValue)) baseMergeDeep(object, source, key, srcIndex, baseMerge, customizer, stack);
		else {
			var newValue = customizer ? customizer(safeGet(object, key), srcValue, key + "", object, source, stack) : void 0;
			if (newValue === void 0) newValue = srcValue;
			assignMergeValue(object, key, newValue);
		}
	}, keysIn);
}
//#endregion
//#region node_modules/lodash-es/_apply.js
/**
* A faster alternative to `Function#apply`, this function invokes `func`
* with the `this` binding of `thisArg` and the arguments of `args`.
*
* @private
* @param {Function} func The function to invoke.
* @param {*} thisArg The `this` binding of `func`.
* @param {Array} args The arguments to invoke `func` with.
* @returns {*} Returns the result of `func`.
*/
function apply(func, thisArg, args) {
	switch (args.length) {
		case 0: return func.call(thisArg);
		case 1: return func.call(thisArg, args[0]);
		case 2: return func.call(thisArg, args[0], args[1]);
		case 3: return func.call(thisArg, args[0], args[1], args[2]);
	}
	return func.apply(thisArg, args);
}
//#endregion
//#region node_modules/lodash-es/_overRest.js
var nativeMax$2 = Math.max;
/**
* A specialized version of `baseRest` which transforms the rest array.
*
* @private
* @param {Function} func The function to apply a rest parameter to.
* @param {number} [start=func.length-1] The start position of the rest parameter.
* @param {Function} transform The rest array transform.
* @returns {Function} Returns the new function.
*/
function overRest(func, start, transform) {
	start = nativeMax$2(start === void 0 ? func.length - 1 : start, 0);
	return function() {
		var args = arguments, index = -1, length = nativeMax$2(args.length - start, 0), array = Array(length);
		while (++index < length) array[index] = args[start + index];
		index = -1;
		var otherArgs = Array(start + 1);
		while (++index < start) otherArgs[index] = args[index];
		otherArgs[start] = transform(array);
		return apply(func, this, otherArgs);
	};
}
//#endregion
//#region node_modules/lodash-es/constant.js
/**
* Creates a function that returns `value`.
*
* @static
* @memberOf _
* @since 2.4.0
* @category Util
* @param {*} value The value to return from the new function.
* @returns {Function} Returns the new constant function.
* @example
*
* var objects = _.times(2, _.constant({ 'a': 1 }));
*
* console.log(objects);
* // => [{ 'a': 1 }, { 'a': 1 }]
*
* console.log(objects[0] === objects[1]);
* // => true
*/
function constant(value) {
	return function() {
		return value;
	};
}
//#endregion
//#region node_modules/lodash-es/_baseSetToString.js
/**
* The base implementation of `setToString` without support for hot loop shorting.
*
* @private
* @param {Function} func The function to modify.
* @param {Function} string The `toString` result.
* @returns {Function} Returns `func`.
*/
var baseSetToString = !defineProperty ? identity : function(func, string) {
	return defineProperty(func, "toString", {
		"configurable": true,
		"enumerable": false,
		"value": constant(string),
		"writable": true
	});
};
//#endregion
//#region node_modules/lodash-es/_shortOut.js
/** Used to detect hot functions by number of calls within a span of milliseconds. */
var HOT_COUNT = 800, HOT_SPAN = 16;
var nativeNow = Date.now;
/**
* Creates a function that'll short out and invoke `identity` instead
* of `func` when it's called `HOT_COUNT` or more times in `HOT_SPAN`
* milliseconds.
*
* @private
* @param {Function} func The function to restrict.
* @returns {Function} Returns the new shortable function.
*/
function shortOut(func) {
	var count = 0, lastCalled = 0;
	return function() {
		var stamp = nativeNow(), remaining = HOT_SPAN - (stamp - lastCalled);
		lastCalled = stamp;
		if (remaining > 0) {
			if (++count >= HOT_COUNT) return arguments[0];
		} else count = 0;
		return func.apply(void 0, arguments);
	};
}
//#endregion
//#region node_modules/lodash-es/_setToString.js
/**
* Sets the `toString` method of `func` to return `string`.
*
* @private
* @param {Function} func The function to modify.
* @param {Function} string The `toString` result.
* @returns {Function} Returns `func`.
*/
var setToString = shortOut(baseSetToString);
//#endregion
//#region node_modules/lodash-es/_baseRest.js
/**
* The base implementation of `_.rest` which doesn't validate or coerce arguments.
*
* @private
* @param {Function} func The function to apply a rest parameter to.
* @param {number} [start=func.length-1] The start position of the rest parameter.
* @returns {Function} Returns the new function.
*/
function baseRest(func, start) {
	return setToString(overRest(func, start, identity), func + "");
}
//#endregion
//#region node_modules/lodash-es/_isIterateeCall.js
/**
* Checks if the given arguments are from an iteratee call.
*
* @private
* @param {*} value The potential iteratee value argument.
* @param {*} index The potential iteratee index or key argument.
* @param {*} object The potential iteratee object argument.
* @returns {boolean} Returns `true` if the arguments are from an iteratee call,
*  else `false`.
*/
function isIterateeCall(value, index, object) {
	if (!isObject(object)) return false;
	var type = typeof index;
	if (type == "number" ? isArrayLike(object) && isIndex(index, object.length) : type == "string" && index in object) return eq(object[index], value);
	return false;
}
//#endregion
//#region node_modules/lodash-es/_createAssigner.js
/**
* Creates a function like `_.assign`.
*
* @private
* @param {Function} assigner The function to assign values.
* @returns {Function} Returns the new assigner function.
*/
function createAssigner(assigner) {
	return baseRest(function(object, sources) {
		var index = -1, length = sources.length, customizer = length > 1 ? sources[length - 1] : void 0, guard = length > 2 ? sources[2] : void 0;
		customizer = assigner.length > 3 && typeof customizer == "function" ? (length--, customizer) : void 0;
		if (guard && isIterateeCall(sources[0], sources[1], guard)) {
			customizer = length < 3 ? void 0 : customizer;
			length = 1;
		}
		object = Object(object);
		while (++index < length) {
			var source = sources[index];
			if (source) assigner(object, source, index, customizer);
		}
		return object;
	});
}
//#endregion
//#region node_modules/lodash-es/merge.js
/**
* This method is like `_.assign` except that it recursively merges own and
* inherited enumerable string keyed properties of source objects into the
* destination object. Source properties that resolve to `undefined` are
* skipped if a destination value exists. Array and plain object properties
* are merged recursively. Other objects and value types are overridden by
* assignment. Source objects are applied from left to right. Subsequent
* sources overwrite property assignments of previous sources.
*
* **Note:** This method mutates `object`.
*
* @static
* @memberOf _
* @since 0.5.0
* @category Object
* @param {Object} object The destination object.
* @param {...Object} [sources] The source objects.
* @returns {Object} Returns `object`.
* @example
*
* var object = {
*   'a': [{ 'b': 2 }, { 'd': 4 }]
* };
*
* var other = {
*   'a': [{ 'c': 3 }, { 'e': 5 }]
* };
*
* _.merge(object, other);
* // => { 'a': [{ 'b': 2, 'c': 3 }, { 'd': 4, 'e': 5 }] }
*/
var merge = createAssigner(function(object, source, srcIndex) {
	baseMerge(object, source, srcIndex);
});
//#endregion
//#region node_modules/lodash-es/_baseAssign.js
/**
* The base implementation of `_.assign` without support for multiple sources
* or `customizer` functions.
*
* @private
* @param {Object} object The destination object.
* @param {Object} source The source object.
* @returns {Object} Returns `object`.
*/
function baseAssign(object, source) {
	return object && copyObject(source, keys(source), object);
}
//#endregion
//#region node_modules/lodash-es/_baseAssignIn.js
/**
* The base implementation of `_.assignIn` without support for multiple sources
* or `customizer` functions.
*
* @private
* @param {Object} object The destination object.
* @param {Object} source The source object.
* @returns {Object} Returns `object`.
*/
function baseAssignIn(object, source) {
	return object && copyObject(source, keysIn(source), object);
}
//#endregion
//#region node_modules/lodash-es/_copySymbols.js
/**
* Copies own symbols of `source` to `object`.
*
* @private
* @param {Object} source The object to copy symbols from.
* @param {Object} [object={}] The object to copy symbols to.
* @returns {Object} Returns `object`.
*/
function copySymbols(source, object) {
	return copyObject(source, getSymbols(source), object);
}
//#endregion
//#region node_modules/lodash-es/_getSymbolsIn.js
/**
* Creates an array of the own and inherited enumerable symbols of `object`.
*
* @private
* @param {Object} object The object to query.
* @returns {Array} Returns the array of symbols.
*/
var getSymbolsIn = !Object.getOwnPropertySymbols ? stubArray : function(object) {
	var result = [];
	while (object) {
		arrayPush(result, getSymbols(object));
		object = getPrototype(object);
	}
	return result;
};
//#endregion
//#region node_modules/lodash-es/_copySymbolsIn.js
/**
* Copies own and inherited symbols of `source` to `object`.
*
* @private
* @param {Object} source The object to copy symbols from.
* @param {Object} [object={}] The object to copy symbols to.
* @returns {Object} Returns `object`.
*/
function copySymbolsIn(source, object) {
	return copyObject(source, getSymbolsIn(source), object);
}
//#endregion
//#region node_modules/lodash-es/_getAllKeysIn.js
/**
* Creates an array of own and inherited enumerable property names and
* symbols of `object`.
*
* @private
* @param {Object} object The object to query.
* @returns {Array} Returns the array of property names and symbols.
*/
function getAllKeysIn(object) {
	return baseGetAllKeys(object, keysIn, getSymbolsIn);
}
//#endregion
//#region node_modules/lodash-es/_initCloneArray.js
/** Used to check objects for own properties. */
var hasOwnProperty$2 = Object.prototype.hasOwnProperty;
/**
* Initializes an array clone.
*
* @private
* @param {Array} array The array to clone.
* @returns {Array} Returns the initialized clone.
*/
function initCloneArray(array) {
	var length = array.length, result = new array.constructor(length);
	if (length && typeof array[0] == "string" && hasOwnProperty$2.call(array, "index")) {
		result.index = array.index;
		result.input = array.input;
	}
	return result;
}
//#endregion
//#region node_modules/lodash-es/_cloneDataView.js
/**
* Creates a clone of `dataView`.
*
* @private
* @param {Object} dataView The data view to clone.
* @param {boolean} [isDeep] Specify a deep clone.
* @returns {Object} Returns the cloned data view.
*/
function cloneDataView(dataView, isDeep) {
	var buffer = isDeep ? cloneArrayBuffer(dataView.buffer) : dataView.buffer;
	return new dataView.constructor(buffer, dataView.byteOffset, dataView.byteLength);
}
//#endregion
//#region node_modules/lodash-es/_cloneRegExp.js
/** Used to match `RegExp` flags from their coerced string values. */
var reFlags = /\w*$/;
/**
* Creates a clone of `regexp`.
*
* @private
* @param {Object} regexp The regexp to clone.
* @returns {Object} Returns the cloned regexp.
*/
function cloneRegExp(regexp) {
	var result = new regexp.constructor(regexp.source, reFlags.exec(regexp));
	result.lastIndex = regexp.lastIndex;
	return result;
}
//#endregion
//#region node_modules/lodash-es/_cloneSymbol.js
/** Used to convert symbols to primitives and strings. */
var symbolProto = Symbol ? Symbol.prototype : void 0, symbolValueOf = symbolProto ? symbolProto.valueOf : void 0;
/**
* Creates a clone of the `symbol` object.
*
* @private
* @param {Object} symbol The symbol object to clone.
* @returns {Object} Returns the cloned symbol object.
*/
function cloneSymbol(symbol) {
	return symbolValueOf ? Object(symbolValueOf.call(symbol)) : {};
}
//#endregion
//#region node_modules/lodash-es/_initCloneByTag.js
/** `Object#toString` result references. */
var boolTag$1 = "[object Boolean]", dateTag$1 = "[object Date]", mapTag$3 = "[object Map]", numberTag$1 = "[object Number]", regexpTag$1 = "[object RegExp]", setTag$3 = "[object Set]", stringTag$2 = "[object String]", symbolTag$1 = "[object Symbol]";
var arrayBufferTag$1 = "[object ArrayBuffer]", dataViewTag$1 = "[object DataView]", float32Tag$1 = "[object Float32Array]", float64Tag$1 = "[object Float64Array]", int8Tag$1 = "[object Int8Array]", int16Tag$1 = "[object Int16Array]", int32Tag$1 = "[object Int32Array]", uint8Tag$1 = "[object Uint8Array]", uint8ClampedTag$1 = "[object Uint8ClampedArray]", uint16Tag$1 = "[object Uint16Array]", uint32Tag$1 = "[object Uint32Array]";
/**
* Initializes an object clone based on its `toStringTag`.
*
* **Note:** This function only supports cloning values with tags of
* `Boolean`, `Date`, `Error`, `Map`, `Number`, `RegExp`, `Set`, or `String`.
*
* @private
* @param {Object} object The object to clone.
* @param {string} tag The `toStringTag` of the object to clone.
* @param {boolean} [isDeep] Specify a deep clone.
* @returns {Object} Returns the initialized clone.
*/
function initCloneByTag(object, tag, isDeep) {
	var Ctor = object.constructor;
	switch (tag) {
		case arrayBufferTag$1: return cloneArrayBuffer(object);
		case boolTag$1:
		case dateTag$1: return new Ctor(+object);
		case dataViewTag$1: return cloneDataView(object, isDeep);
		case float32Tag$1:
		case float64Tag$1:
		case int8Tag$1:
		case int16Tag$1:
		case int32Tag$1:
		case uint8Tag$1:
		case uint8ClampedTag$1:
		case uint16Tag$1:
		case uint32Tag$1: return cloneTypedArray(object, isDeep);
		case mapTag$3: return new Ctor();
		case numberTag$1:
		case stringTag$2: return new Ctor(object);
		case regexpTag$1: return cloneRegExp(object);
		case setTag$3: return new Ctor();
		case symbolTag$1: return cloneSymbol(object);
	}
}
//#endregion
//#region node_modules/lodash-es/_baseIsMap.js
/** `Object#toString` result references. */
var mapTag$2 = "[object Map]";
/**
* The base implementation of `_.isMap` without Node.js optimizations.
*
* @private
* @param {*} value The value to check.
* @returns {boolean} Returns `true` if `value` is a map, else `false`.
*/
function baseIsMap(value) {
	return isObjectLike(value) && _getTag_default(value) == mapTag$2;
}
//#endregion
//#region node_modules/lodash-es/isMap.js
var nodeIsMap = nodeUtil && nodeUtil.isMap;
/**
* Checks if `value` is classified as a `Map` object.
*
* @static
* @memberOf _
* @since 4.3.0
* @category Lang
* @param {*} value The value to check.
* @returns {boolean} Returns `true` if `value` is a map, else `false`.
* @example
*
* _.isMap(new Map);
* // => true
*
* _.isMap(new WeakMap);
* // => false
*/
var isMap = nodeIsMap ? baseUnary(nodeIsMap) : baseIsMap;
//#endregion
//#region node_modules/lodash-es/_baseIsSet.js
/** `Object#toString` result references. */
var setTag$2 = "[object Set]";
/**
* The base implementation of `_.isSet` without Node.js optimizations.
*
* @private
* @param {*} value The value to check.
* @returns {boolean} Returns `true` if `value` is a set, else `false`.
*/
function baseIsSet(value) {
	return isObjectLike(value) && _getTag_default(value) == setTag$2;
}
//#endregion
//#region node_modules/lodash-es/isSet.js
var nodeIsSet = nodeUtil && nodeUtil.isSet;
/**
* Checks if `value` is classified as a `Set` object.
*
* @static
* @memberOf _
* @since 4.3.0
* @category Lang
* @param {*} value The value to check.
* @returns {boolean} Returns `true` if `value` is a set, else `false`.
* @example
*
* _.isSet(new Set);
* // => true
*
* _.isSet(new WeakSet);
* // => false
*/
var isSet = nodeIsSet ? baseUnary(nodeIsSet) : baseIsSet;
//#endregion
//#region node_modules/lodash-es/_baseClone.js
/** Used to compose bitmasks for cloning. */
var CLONE_DEEP_FLAG$1 = 1, CLONE_FLAT_FLAG = 2, CLONE_SYMBOLS_FLAG$2 = 4;
/** `Object#toString` result references. */
var argsTag = "[object Arguments]", arrayTag = "[object Array]", boolTag = "[object Boolean]", dateTag = "[object Date]", errorTag = "[object Error]", funcTag = "[object Function]", genTag = "[object GeneratorFunction]", mapTag$1 = "[object Map]", numberTag = "[object Number]", objectTag = "[object Object]", regexpTag = "[object RegExp]", setTag$1 = "[object Set]", stringTag$1 = "[object String]", symbolTag = "[object Symbol]", weakMapTag = "[object WeakMap]";
var arrayBufferTag = "[object ArrayBuffer]", dataViewTag = "[object DataView]", float32Tag = "[object Float32Array]", float64Tag = "[object Float64Array]", int8Tag = "[object Int8Array]", int16Tag = "[object Int16Array]", int32Tag = "[object Int32Array]", uint8Tag = "[object Uint8Array]", uint8ClampedTag = "[object Uint8ClampedArray]", uint16Tag = "[object Uint16Array]", uint32Tag = "[object Uint32Array]";
/** Used to identify `toStringTag` values supported by `_.clone`. */
var cloneableTags = {};
cloneableTags[argsTag] = cloneableTags[arrayTag] = cloneableTags[arrayBufferTag] = cloneableTags[dataViewTag] = cloneableTags[boolTag] = cloneableTags[dateTag] = cloneableTags[float32Tag] = cloneableTags[float64Tag] = cloneableTags[int8Tag] = cloneableTags[int16Tag] = cloneableTags[int32Tag] = cloneableTags[mapTag$1] = cloneableTags[numberTag] = cloneableTags[objectTag] = cloneableTags[regexpTag] = cloneableTags[setTag$1] = cloneableTags[stringTag$1] = cloneableTags[symbolTag] = cloneableTags[uint8Tag] = cloneableTags[uint8ClampedTag] = cloneableTags[uint16Tag] = cloneableTags[uint32Tag] = true;
cloneableTags[errorTag] = cloneableTags[funcTag] = cloneableTags[weakMapTag] = false;
/**
* The base implementation of `_.clone` and `_.cloneDeep` which tracks
* traversed objects.
*
* @private
* @param {*} value The value to clone.
* @param {boolean} bitmask The bitmask flags.
*  1 - Deep clone
*  2 - Flatten inherited properties
*  4 - Clone symbols
* @param {Function} [customizer] The function to customize cloning.
* @param {string} [key] The key of `value`.
* @param {Object} [object] The parent object of `value`.
* @param {Object} [stack] Tracks traversed objects and their clone counterparts.
* @returns {*} Returns the cloned value.
*/
function baseClone(value, bitmask, customizer, key, object, stack) {
	var result, isDeep = bitmask & CLONE_DEEP_FLAG$1, isFlat = bitmask & CLONE_FLAT_FLAG, isFull = bitmask & CLONE_SYMBOLS_FLAG$2;
	if (customizer) result = object ? customizer(value, key, object, stack) : customizer(value);
	if (result !== void 0) return result;
	if (!isObject(value)) return value;
	var isArr = isArray(value);
	if (isArr) {
		result = initCloneArray(value);
		if (!isDeep) return copyArray(value, result);
	} else {
		var tag = _getTag_default(value), isFunc = tag == funcTag || tag == genTag;
		if (isBuffer(value)) return cloneBuffer(value, isDeep);
		if (tag == objectTag || tag == argsTag || isFunc && !object) {
			result = isFlat || isFunc ? {} : initCloneObject(value);
			if (!isDeep) return isFlat ? copySymbolsIn(value, baseAssignIn(result, value)) : copySymbols(value, baseAssign(result, value));
		} else {
			if (!cloneableTags[tag]) return object ? value : {};
			result = initCloneByTag(value, tag, isDeep);
		}
	}
	stack || (stack = new Stack());
	var stacked = stack.get(value);
	if (stacked) return stacked;
	stack.set(value, result);
	if (isSet(value)) value.forEach(function(subValue) {
		result.add(baseClone(subValue, bitmask, customizer, subValue, value, stack));
	});
	else if (isMap(value)) value.forEach(function(subValue, key) {
		result.set(key, baseClone(subValue, bitmask, customizer, key, value, stack));
	});
	var props = isArr ? void 0 : (isFull ? isFlat ? getAllKeysIn : getAllKeys : isFlat ? keysIn : keys)(value);
	arrayEach(props || value, function(subValue, key) {
		if (props) {
			key = subValue;
			subValue = value[key];
		}
		assignValue(result, key, baseClone(subValue, bitmask, customizer, key, value, stack));
	});
	return result;
}
//#endregion
//#region node_modules/lodash-es/clone.js
/** Used to compose bitmasks for cloning. */
var CLONE_SYMBOLS_FLAG$1 = 4;
/**
* Creates a shallow clone of `value`.
*
* **Note:** This method is loosely based on the
* [structured clone algorithm](https://mdn.io/Structured_clone_algorithm)
* and supports cloning arrays, array buffers, booleans, date objects, maps,
* numbers, `Object` objects, regexes, sets, strings, symbols, and typed
* arrays. The own enumerable properties of `arguments` objects are cloned
* as plain objects. An empty object is returned for uncloneable values such
* as error objects, functions, DOM nodes, and WeakMaps.
*
* @static
* @memberOf _
* @since 0.1.0
* @category Lang
* @param {*} value The value to clone.
* @returns {*} Returns the cloned value.
* @see _.cloneDeep
* @example
*
* var objects = [{ 'a': 1 }, { 'b': 2 }];
*
* var shallow = _.clone(objects);
* console.log(shallow[0] === objects[0]);
* // => true
*/
function clone(value) {
	return baseClone(value, CLONE_SYMBOLS_FLAG$1);
}
//#endregion
//#region node_modules/lodash-es/_trimmedEndIndex.js
/** Used to match a single whitespace character. */
var reWhitespace = /\s/;
/**
* Used by `_.trim` and `_.trimEnd` to get the index of the last non-whitespace
* character of `string`.
*
* @private
* @param {string} string The string to inspect.
* @returns {number} Returns the index of the last non-whitespace character.
*/
function trimmedEndIndex(string) {
	var index = string.length;
	while (index-- && reWhitespace.test(string.charAt(index)));
	return index;
}
//#endregion
//#region node_modules/lodash-es/_baseTrim.js
/** Used to match leading whitespace. */
var reTrimStart = /^\s+/;
/**
* The base implementation of `_.trim`.
*
* @private
* @param {string} string The string to trim.
* @returns {string} Returns the trimmed string.
*/
function baseTrim(string) {
	return string ? string.slice(0, trimmedEndIndex(string) + 1).replace(reTrimStart, "") : string;
}
//#endregion
//#region node_modules/lodash-es/toNumber.js
/** Used as references for various `Number` constants. */
var NAN = NaN;
/** Used to detect bad signed hexadecimal string values. */
var reIsBadHex = /^[-+]0x[0-9a-f]+$/i;
/** Used to detect binary string values. */
var reIsBinary = /^0b[01]+$/i;
/** Used to detect octal string values. */
var reIsOctal = /^0o[0-7]+$/i;
/** Built-in method references without a dependency on `root`. */
var freeParseInt = parseInt;
/**
* Converts `value` to a number.
*
* @static
* @memberOf _
* @since 4.0.0
* @category Lang
* @param {*} value The value to process.
* @returns {number} Returns the number.
* @example
*
* _.toNumber(3.2);
* // => 3.2
*
* _.toNumber(Number.MIN_VALUE);
* // => 5e-324
*
* _.toNumber(Infinity);
* // => Infinity
*
* _.toNumber('3.2');
* // => 3.2
*/
function toNumber(value) {
	if (typeof value == "number") return value;
	if (isSymbol(value)) return NAN;
	if (isObject(value)) {
		var other = typeof value.valueOf == "function" ? value.valueOf() : value;
		value = isObject(other) ? other + "" : other;
	}
	if (typeof value != "string") return value === 0 ? value : +value;
	value = baseTrim(value);
	var isBinary = reIsBinary.test(value);
	return isBinary || reIsOctal.test(value) ? freeParseInt(value.slice(2), isBinary ? 2 : 8) : reIsBadHex.test(value) ? NAN : +value;
}
//#endregion
//#region node_modules/lodash-es/toFinite.js
/** Used as references for various `Number` constants. */
var INFINITY = Infinity, MAX_INTEGER = 17976931348623157e292;
/**
* Converts `value` to a finite number.
*
* @static
* @memberOf _
* @since 4.12.0
* @category Lang
* @param {*} value The value to convert.
* @returns {number} Returns the converted number.
* @example
*
* _.toFinite(3.2);
* // => 3.2
*
* _.toFinite(Number.MIN_VALUE);
* // => 5e-324
*
* _.toFinite(Infinity);
* // => 1.7976931348623157e+308
*
* _.toFinite('3.2');
* // => 3.2
*/
function toFinite(value) {
	if (!value) return value === 0 ? value : 0;
	value = toNumber(value);
	if (value === INFINITY || value === -INFINITY) return (value < 0 ? -1 : 1) * MAX_INTEGER;
	return value === value ? value : 0;
}
//#endregion
//#region node_modules/lodash-es/toInteger.js
/**
* Converts `value` to an integer.
*
* **Note:** This method is loosely based on
* [`ToInteger`](http://www.ecma-international.org/ecma-262/7.0/#sec-tointeger).
*
* @static
* @memberOf _
* @since 4.0.0
* @category Lang
* @param {*} value The value to convert.
* @returns {number} Returns the converted integer.
* @example
*
* _.toInteger(3.2);
* // => 3
*
* _.toInteger(Number.MIN_VALUE);
* // => 0
*
* _.toInteger(Infinity);
* // => 1.7976931348623157e+308
*
* _.toInteger('3.2');
* // => 3
*/
function toInteger(value) {
	var result = toFinite(value), remainder = result % 1;
	return result === result ? remainder ? result - remainder : result : 0;
}
//#endregion
//#region node_modules/lodash-es/_flatRest.js
/**
* A specialized version of `baseRest` which flattens the rest array.
*
* @private
* @param {Function} func The function to apply a rest parameter to.
* @returns {Function} Returns the new function.
*/
function flatRest(func) {
	return setToString(overRest(func, void 0, flatten), func + "");
}
//#endregion
//#region node_modules/lodash-es/_hasUnicode.js
/** Used to detect strings with [zero-width joiners or code points from the astral planes](http://eev.ee/blog/2015/09/12/dark-corners-of-unicode/). */
var reHasUnicode = RegExp("[\\u200d\\ud800-\\udfff\\u0300-\\u036f\\ufe20-\\ufe2f\\u20d0-\\u20ff\\ufe0e\\ufe0f]");
/**
* Checks if `string` contains Unicode symbols.
*
* @private
* @param {string} string The string to inspect.
* @returns {boolean} Returns `true` if a symbol is found, else `false`.
*/
function hasUnicode(string) {
	return reHasUnicode.test(string);
}
//#endregion
//#region node_modules/lodash-es/cloneDeep.js
/** Used to compose bitmasks for cloning. */
var CLONE_DEEP_FLAG = 1, CLONE_SYMBOLS_FLAG = 4;
/**
* This method is like `_.clone` except that it recursively clones `value`.
*
* @static
* @memberOf _
* @since 1.0.0
* @category Lang
* @param {*} value The value to recursively clone.
* @returns {*} Returns the deep cloned value.
* @see _.clone
* @example
*
* var objects = [{ 'a': 1 }, { 'b': 2 }];
*
* var deep = _.cloneDeep(objects);
* console.log(deep[0] === objects[0]);
* // => false
*/
function cloneDeep(value) {
	return baseClone(value, CLONE_DEEP_FLAG | CLONE_SYMBOLS_FLAG);
}
//#endregion
//#region node_modules/lodash-es/now.js
/**
* Gets the timestamp of the number of milliseconds that have elapsed since
* the Unix epoch (1 January 1970 00:00:00 UTC).
*
* @static
* @memberOf _
* @since 2.4.0
* @category Date
* @returns {number} Returns the timestamp.
* @example
*
* _.defer(function(stamp) {
*   console.log(_.now() - stamp);
* }, _.now());
* // => Logs the number of milliseconds it took for the deferred invocation.
*/
var now = function() {
	return root.Date.now();
};
//#endregion
//#region node_modules/lodash-es/defaults.js
/** Used for built-in method references. */
var objectProto = Object.prototype;
/** Used to check objects for own properties. */
var hasOwnProperty$1 = objectProto.hasOwnProperty;
/**
* Assigns own and inherited enumerable string keyed properties of source
* objects to the destination object for all destination properties that
* resolve to `undefined`. Source objects are applied from left to right.
* Once a property is set, additional values of the same property are ignored.
*
* **Note:** This method mutates `object`.
*
* @static
* @since 0.1.0
* @memberOf _
* @category Object
* @param {Object} object The destination object.
* @param {...Object} [sources] The source objects.
* @returns {Object} Returns `object`.
* @see _.defaultsDeep
* @example
*
* _.defaults({ 'a': 1 }, { 'b': 2 }, { 'a': 3 });
* // => { 'a': 1, 'b': 2 }
*/
var defaults = baseRest(function(object, sources) {
	object = Object(object);
	var index = -1;
	var length = sources.length;
	var guard = length > 2 ? sources[2] : void 0;
	if (guard && isIterateeCall(sources[0], sources[1], guard)) length = 1;
	while (++index < length) {
		var source = sources[index];
		var props = keysIn(source);
		var propsIndex = -1;
		var propsLength = props.length;
		while (++propsIndex < propsLength) {
			var key = props[propsIndex];
			var value = object[key];
			if (value === void 0 || eq(value, objectProto[key]) && !hasOwnProperty$1.call(object, key)) object[key] = source[key];
		}
	}
	return object;
});
//#endregion
//#region node_modules/lodash-es/last.js
/**
* Gets the last element of `array`.
*
* @static
* @memberOf _
* @since 0.1.0
* @category Array
* @param {Array} array The array to query.
* @returns {*} Returns the last element of `array`.
* @example
*
* _.last([1, 2, 3]);
* // => 3
*/
function last(array) {
	var length = array == null ? 0 : array.length;
	return length ? array[length - 1] : void 0;
}
//#endregion
//#region node_modules/lodash-es/_createFind.js
/**
* Creates a `_.find` or `_.findLast` function.
*
* @private
* @param {Function} findIndexFunc The function to find the collection index.
* @returns {Function} Returns the new find function.
*/
function createFind(findIndexFunc) {
	return function(collection, predicate, fromIndex) {
		var iterable = Object(collection);
		if (!isArrayLike(collection)) {
			var iteratee = baseIteratee(predicate, 3);
			collection = keys(collection);
			predicate = function(key) {
				return iteratee(iterable[key], key, iterable);
			};
		}
		var index = findIndexFunc(collection, predicate, fromIndex);
		return index > -1 ? iterable[iteratee ? collection[index] : index] : void 0;
	};
}
//#endregion
//#region node_modules/lodash-es/findIndex.js
var nativeMax$1 = Math.max;
/**
* This method is like `_.find` except that it returns the index of the first
* element `predicate` returns truthy for instead of the element itself.
*
* @static
* @memberOf _
* @since 1.1.0
* @category Array
* @param {Array} array The array to inspect.
* @param {Function} [predicate=_.identity] The function invoked per iteration.
* @param {number} [fromIndex=0] The index to search from.
* @returns {number} Returns the index of the found element, else `-1`.
* @example
*
* var users = [
*   { 'user': 'barney',  'active': false },
*   { 'user': 'fred',    'active': false },
*   { 'user': 'pebbles', 'active': true }
* ];
*
* _.findIndex(users, function(o) { return o.user == 'barney'; });
* // => 0
*
* // The `_.matches` iteratee shorthand.
* _.findIndex(users, { 'user': 'fred', 'active': false });
* // => 1
*
* // The `_.matchesProperty` iteratee shorthand.
* _.findIndex(users, ['active', false]);
* // => 0
*
* // The `_.property` iteratee shorthand.
* _.findIndex(users, 'active');
* // => 2
*/
function findIndex(array, predicate, fromIndex) {
	var length = array == null ? 0 : array.length;
	if (!length) return -1;
	var index = fromIndex == null ? 0 : toInteger(fromIndex);
	if (index < 0) index = nativeMax$1(length + index, 0);
	return baseFindIndex(array, baseIteratee(predicate, 3), index);
}
//#endregion
//#region node_modules/lodash-es/find.js
/**
* Iterates over elements of `collection`, returning the first element
* `predicate` returns truthy for. The predicate is invoked with three
* arguments: (value, index|key, collection).
*
* @static
* @memberOf _
* @since 0.1.0
* @category Collection
* @param {Array|Object} collection The collection to inspect.
* @param {Function} [predicate=_.identity] The function invoked per iteration.
* @param {number} [fromIndex=0] The index to search from.
* @returns {*} Returns the matched element, else `undefined`.
* @example
*
* var users = [
*   { 'user': 'barney',  'age': 36, 'active': true },
*   { 'user': 'fred',    'age': 40, 'active': false },
*   { 'user': 'pebbles', 'age': 1,  'active': true }
* ];
*
* _.find(users, function(o) { return o.age < 40; });
* // => object for 'barney'
*
* // The `_.matches` iteratee shorthand.
* _.find(users, { 'age': 1, 'active': true });
* // => object for 'pebbles'
*
* // The `_.matchesProperty` iteratee shorthand.
* _.find(users, ['active', false]);
* // => object for 'fred'
*
* // The `_.property` iteratee shorthand.
* _.find(users, 'active');
* // => object for 'barney'
*/
var find = createFind(findIndex);
//#endregion
//#region node_modules/lodash-es/forIn.js
/**
* Iterates over own and inherited enumerable string keyed properties of an
* object and invokes `iteratee` for each property. The iteratee is invoked
* with three arguments: (value, key, object). Iteratee functions may exit
* iteration early by explicitly returning `false`.
*
* @static
* @memberOf _
* @since 0.3.0
* @category Object
* @param {Object} object The object to iterate over.
* @param {Function} [iteratee=_.identity] The function invoked per iteration.
* @returns {Object} Returns `object`.
* @see _.forInRight
* @example
*
* function Foo() {
*   this.a = 1;
*   this.b = 2;
* }
*
* Foo.prototype.c = 3;
*
* _.forIn(new Foo, function(value, key) {
*   console.log(key);
* });
* // => Logs 'a', 'b', then 'c' (iteration order is not guaranteed).
*/
function forIn(object, iteratee) {
	return object == null ? object : baseFor(object, castFunction(iteratee), keysIn);
}
//#endregion
//#region node_modules/lodash-es/forOwn.js
/**
* Iterates over own enumerable string keyed properties of an object and
* invokes `iteratee` for each property. The iteratee is invoked with three
* arguments: (value, key, object). Iteratee functions may exit iteration
* early by explicitly returning `false`.
*
* @static
* @memberOf _
* @since 0.3.0
* @category Object
* @param {Object} object The object to iterate over.
* @param {Function} [iteratee=_.identity] The function invoked per iteration.
* @returns {Object} Returns `object`.
* @see _.forOwnRight
* @example
*
* function Foo() {
*   this.a = 1;
*   this.b = 2;
* }
*
* Foo.prototype.c = 3;
*
* _.forOwn(new Foo, function(value, key) {
*   console.log(key);
* });
* // => Logs 'a' then 'b' (iteration order is not guaranteed).
*/
function forOwn(object, iteratee) {
	return object && baseForOwn(object, castFunction(iteratee));
}
//#endregion
//#region node_modules/lodash-es/_baseGt.js
/**
* The base implementation of `_.gt` which doesn't coerce arguments.
*
* @private
* @param {*} value The value to compare.
* @param {*} other The other value to compare.
* @returns {boolean} Returns `true` if `value` is greater than `other`,
*  else `false`.
*/
function baseGt(value, other) {
	return value > other;
}
//#endregion
//#region node_modules/lodash-es/_baseHas.js
/** Used to check objects for own properties. */
var hasOwnProperty = Object.prototype.hasOwnProperty;
/**
* The base implementation of `_.has` without support for deep paths.
*
* @private
* @param {Object} [object] The object to query.
* @param {Array|string} key The key to check.
* @returns {boolean} Returns `true` if `key` exists, else `false`.
*/
function baseHas(object, key) {
	return object != null && hasOwnProperty.call(object, key);
}
//#endregion
//#region node_modules/lodash-es/has.js
/**
* Checks if `path` is a direct property of `object`.
*
* @static
* @since 0.1.0
* @memberOf _
* @category Object
* @param {Object} object The object to query.
* @param {Array|string} path The path to check.
* @returns {boolean} Returns `true` if `path` exists, else `false`.
* @example
*
* var object = { 'a': { 'b': 2 } };
* var other = _.create({ 'a': _.create({ 'b': 2 }) });
*
* _.has(object, 'a');
* // => true
*
* _.has(object, 'a.b');
* // => true
*
* _.has(object, ['a', 'b']);
* // => true
*
* _.has(other, 'a');
* // => false
*/
function has(object, path) {
	return object != null && hasPath(object, path, baseHas);
}
//#endregion
//#region node_modules/lodash-es/isString.js
/** `Object#toString` result references. */
var stringTag = "[object String]";
/**
* Checks if `value` is classified as a `String` primitive or object.
*
* @static
* @since 0.1.0
* @memberOf _
* @category Lang
* @param {*} value The value to check.
* @returns {boolean} Returns `true` if `value` is a string, else `false`.
* @example
*
* _.isString('abc');
* // => true
*
* _.isString(1);
* // => false
*/
function isString(value) {
	return typeof value == "string" || !isArray(value) && isObjectLike(value) && baseGetTag(value) == stringTag;
}
//#endregion
//#region node_modules/lodash-es/_baseValues.js
/**
* The base implementation of `_.values` and `_.valuesIn` which creates an
* array of `object` property values corresponding to the property names
* of `props`.
*
* @private
* @param {Object} object The object to query.
* @param {Array} props The property names to get values for.
* @returns {Object} Returns the array of property values.
*/
function baseValues(object, props) {
	return arrayMap(props, function(key) {
		return object[key];
	});
}
//#endregion
//#region node_modules/lodash-es/values.js
/**
* Creates an array of the own enumerable string keyed property values of `object`.
*
* **Note:** Non-object values are coerced to objects.
*
* @static
* @since 0.1.0
* @memberOf _
* @category Object
* @param {Object} object The object to query.
* @returns {Array} Returns the array of property values.
* @example
*
* function Foo() {
*   this.a = 1;
*   this.b = 2;
* }
*
* Foo.prototype.c = 3;
*
* _.values(new Foo);
* // => [1, 2] (iteration order is not guaranteed)
*
* _.values('hi');
* // => ['h', 'i']
*/
function values(object) {
	return object == null ? [] : baseValues(object, keys(object));
}
//#endregion
//#region node_modules/lodash-es/isUndefined.js
/**
* Checks if `value` is `undefined`.
*
* @static
* @since 0.1.0
* @memberOf _
* @category Lang
* @param {*} value The value to check.
* @returns {boolean} Returns `true` if `value` is `undefined`, else `false`.
* @example
*
* _.isUndefined(void 0);
* // => true
*
* _.isUndefined(null);
* // => false
*/
function isUndefined(value) {
	return value === void 0;
}
//#endregion
//#region node_modules/lodash-es/mapValues.js
/**
* Creates an object with the same keys as `object` and values generated
* by running each own enumerable string keyed property of `object` thru
* `iteratee`. The iteratee is invoked with three arguments:
* (value, key, object).
*
* @static
* @memberOf _
* @since 2.4.0
* @category Object
* @param {Object} object The object to iterate over.
* @param {Function} [iteratee=_.identity] The function invoked per iteration.
* @returns {Object} Returns the new mapped object.
* @see _.mapKeys
* @example
*
* var users = {
*   'fred':    { 'user': 'fred',    'age': 40 },
*   'pebbles': { 'user': 'pebbles', 'age': 1 }
* };
*
* _.mapValues(users, function(o) { return o.age; });
* // => { 'fred': 40, 'pebbles': 1 } (iteration order is not guaranteed)
*
* // The `_.property` iteratee shorthand.
* _.mapValues(users, 'age');
* // => { 'fred': 40, 'pebbles': 1 } (iteration order is not guaranteed)
*/
function mapValues(object, iteratee) {
	var result = {};
	iteratee = baseIteratee(iteratee, 3);
	baseForOwn(object, function(value, key, object) {
		baseAssignValue(result, key, iteratee(value, key, object));
	});
	return result;
}
//#endregion
//#region node_modules/lodash-es/max.js
/**
* Computes the maximum value of `array`. If `array` is empty or falsey,
* `undefined` is returned.
*
* @static
* @since 0.1.0
* @memberOf _
* @category Math
* @param {Array} array The array to iterate over.
* @returns {*} Returns the maximum value.
* @example
*
* _.max([4, 2, 8, 6]);
* // => 8
*
* _.max([]);
* // => undefined
*/
function max(array) {
	return array && array.length ? baseExtremum(array, identity, baseGt) : void 0;
}
//#endregion
//#region node_modules/lodash-es/minBy.js
/**
* This method is like `_.min` except that it accepts `iteratee` which is
* invoked for each element in `array` to generate the criterion by which
* the value is ranked. The iteratee is invoked with one argument: (value).
*
* @static
* @memberOf _
* @since 4.0.0
* @category Math
* @param {Array} array The array to iterate over.
* @param {Function} [iteratee=_.identity] The iteratee invoked per element.
* @returns {*} Returns the minimum value.
* @example
*
* var objects = [{ 'n': 1 }, { 'n': 2 }];
*
* _.minBy(objects, function(o) { return o.n; });
* // => { 'n': 1 }
*
* // The `_.property` iteratee shorthand.
* _.minBy(objects, 'n');
* // => { 'n': 1 }
*/
function minBy(array, iteratee) {
	return array && array.length ? baseExtremum(array, baseIteratee(iteratee, 2), baseLt) : void 0;
}
//#endregion
//#region node_modules/lodash-es/_baseSet.js
/**
* The base implementation of `_.set`.
*
* @private
* @param {Object} object The object to modify.
* @param {Array|string} path The path of the property to set.
* @param {*} value The value to set.
* @param {Function} [customizer] The function to customize path creation.
* @returns {Object} Returns `object`.
*/
function baseSet(object, path, value, customizer) {
	if (!isObject(object)) return object;
	path = castPath(path, object);
	var index = -1, length = path.length, lastIndex = length - 1, nested = object;
	while (nested != null && ++index < length) {
		var key = toKey(path[index]), newValue = value;
		if (key === "__proto__" || key === "constructor" || key === "prototype") return object;
		if (index != lastIndex) {
			var objValue = nested[key];
			newValue = customizer ? customizer(objValue, key, nested) : void 0;
			if (newValue === void 0) newValue = isObject(objValue) ? objValue : isIndex(path[index + 1]) ? [] : {};
		}
		assignValue(nested, key, newValue);
		nested = nested[key];
	}
	return object;
}
//#endregion
//#region node_modules/lodash-es/_basePickBy.js
/**
* The base implementation of  `_.pickBy` without support for iteratee shorthands.
*
* @private
* @param {Object} object The source object.
* @param {string[]} paths The property paths to pick.
* @param {Function} predicate The function invoked per property.
* @returns {Object} Returns the new object.
*/
function basePickBy(object, paths, predicate) {
	var index = -1, length = paths.length, result = {};
	while (++index < length) {
		var path = paths[index], value = baseGet(object, path);
		if (predicate(value, path)) baseSet(result, castPath(path, object), value);
	}
	return result;
}
//#endregion
//#region node_modules/lodash-es/_baseSortBy.js
/**
* The base implementation of `_.sortBy` which uses `comparer` to define the
* sort order of `array` and replaces criteria objects with their corresponding
* values.
*
* @private
* @param {Array} array The array to sort.
* @param {Function} comparer The function to define sort order.
* @returns {Array} Returns `array`.
*/
function baseSortBy(array, comparer) {
	var length = array.length;
	array.sort(comparer);
	while (length--) array[length] = array[length].value;
	return array;
}
//#endregion
//#region node_modules/lodash-es/_compareAscending.js
/**
* Compares values to sort them in ascending order.
*
* @private
* @param {*} value The value to compare.
* @param {*} other The other value to compare.
* @returns {number} Returns the sort order indicator for `value`.
*/
function compareAscending(value, other) {
	if (value !== other) {
		var valIsDefined = value !== void 0, valIsNull = value === null, valIsReflexive = value === value, valIsSymbol = isSymbol(value);
		var othIsDefined = other !== void 0, othIsNull = other === null, othIsReflexive = other === other, othIsSymbol = isSymbol(other);
		if (!othIsNull && !othIsSymbol && !valIsSymbol && value > other || valIsSymbol && othIsDefined && othIsReflexive && !othIsNull && !othIsSymbol || valIsNull && othIsDefined && othIsReflexive || !valIsDefined && othIsReflexive || !valIsReflexive) return 1;
		if (!valIsNull && !valIsSymbol && !othIsSymbol && value < other || othIsSymbol && valIsDefined && valIsReflexive && !valIsNull && !valIsSymbol || othIsNull && valIsDefined && valIsReflexive || !othIsDefined && valIsReflexive || !othIsReflexive) return -1;
	}
	return 0;
}
//#endregion
//#region node_modules/lodash-es/_compareMultiple.js
/**
* Used by `_.orderBy` to compare multiple properties of a value to another
* and stable sort them.
*
* If `orders` is unspecified, all values are sorted in ascending order. Otherwise,
* specify an order of "desc" for descending or "asc" for ascending sort order
* of corresponding values.
*
* @private
* @param {Object} object The object to compare.
* @param {Object} other The other object to compare.
* @param {boolean[]|string[]} orders The order to sort by for each property.
* @returns {number} Returns the sort order indicator for `object`.
*/
function compareMultiple(object, other, orders) {
	var index = -1, objCriteria = object.criteria, othCriteria = other.criteria, length = objCriteria.length, ordersLength = orders.length;
	while (++index < length) {
		var result = compareAscending(objCriteria[index], othCriteria[index]);
		if (result) {
			if (index >= ordersLength) return result;
			return result * (orders[index] == "desc" ? -1 : 1);
		}
	}
	return object.index - other.index;
}
//#endregion
//#region node_modules/lodash-es/_baseOrderBy.js
/**
* The base implementation of `_.orderBy` without param guards.
*
* @private
* @param {Array|Object} collection The collection to iterate over.
* @param {Function[]|Object[]|string[]} iteratees The iteratees to sort by.
* @param {string[]} orders The sort orders of `iteratees`.
* @returns {Array} Returns the new sorted array.
*/
function baseOrderBy(collection, iteratees, orders) {
	if (iteratees.length) iteratees = arrayMap(iteratees, function(iteratee) {
		if (isArray(iteratee)) return function(value) {
			return baseGet(value, iteratee.length === 1 ? iteratee[0] : iteratee);
		};
		return iteratee;
	});
	else iteratees = [identity];
	var index = -1;
	iteratees = arrayMap(iteratees, baseUnary(baseIteratee));
	return baseSortBy(baseMap(collection, function(value, key, collection) {
		return {
			"criteria": arrayMap(iteratees, function(iteratee) {
				return iteratee(value);
			}),
			"index": ++index,
			"value": value
		};
	}), function(object, other) {
		return compareMultiple(object, other, orders);
	});
}
//#endregion
//#region node_modules/lodash-es/_asciiSize.js
/**
* Gets the size of an ASCII `string`.
*
* @private
* @param {string} string The string inspect.
* @returns {number} Returns the string size.
*/
var asciiSize = baseProperty("length");
//#endregion
//#region node_modules/lodash-es/_unicodeSize.js
/** Used to compose unicode character classes. */
var rsAstralRange = "\\ud800-\\udfff", rsComboRange = "\\u0300-\\u036f\\ufe20-\\ufe2f\\u20d0-\\u20ff", rsVarRange = "\\ufe0e\\ufe0f";
/** Used to compose unicode capture groups. */
var rsAstral = "[" + rsAstralRange + "]", rsCombo = "[" + rsComboRange + "]", rsFitz = "\\ud83c[\\udffb-\\udfff]", rsModifier = "(?:" + rsCombo + "|" + rsFitz + ")", rsNonAstral = "[^" + rsAstralRange + "]", rsRegional = "(?:\\ud83c[\\udde6-\\uddff]){2}", rsSurrPair = "[\\ud800-\\udbff][\\udc00-\\udfff]", rsZWJ = "\\u200d";
/** Used to compose unicode regexes. */
var reOptMod = rsModifier + "?", rsOptVar = "[" + rsVarRange + "]?", rsOptJoin = "(?:" + rsZWJ + "(?:" + [
	rsNonAstral,
	rsRegional,
	rsSurrPair
].join("|") + ")" + rsOptVar + reOptMod + ")*", rsSeq = rsOptVar + reOptMod + rsOptJoin, rsSymbol = "(?:" + [
	rsNonAstral + rsCombo + "?",
	rsCombo,
	rsRegional,
	rsSurrPair,
	rsAstral
].join("|") + ")";
/** Used to match [string symbols](https://mathiasbynens.be/notes/javascript-unicode). */
var reUnicode = RegExp(rsFitz + "(?=" + rsFitz + ")|" + rsSymbol + rsSeq, "g");
/**
* Gets the size of a Unicode `string`.
*
* @private
* @param {string} string The string inspect.
* @returns {number} Returns the string size.
*/
function unicodeSize(string) {
	var result = reUnicode.lastIndex = 0;
	while (reUnicode.test(string)) ++result;
	return result;
}
//#endregion
//#region node_modules/lodash-es/_stringSize.js
/**
* Gets the number of symbols in `string`.
*
* @private
* @param {string} string The string to inspect.
* @returns {number} Returns the string size.
*/
function stringSize(string) {
	return hasUnicode(string) ? unicodeSize(string) : asciiSize(string);
}
//#endregion
//#region node_modules/lodash-es/_basePick.js
/**
* The base implementation of `_.pick` without support for individual
* property identifiers.
*
* @private
* @param {Object} object The source object.
* @param {string[]} paths The property paths to pick.
* @returns {Object} Returns the new object.
*/
function basePick(object, paths) {
	return basePickBy(object, paths, function(value, path) {
		return hasIn(object, path);
	});
}
//#endregion
//#region node_modules/lodash-es/pick.js
/**
* Creates an object composed of the picked `object` properties.
*
* @static
* @since 0.1.0
* @memberOf _
* @category Object
* @param {Object} object The source object.
* @param {...(string|string[])} [paths] The property paths to pick.
* @returns {Object} Returns the new object.
* @example
*
* var object = { 'a': 1, 'b': '2', 'c': 3 };
*
* _.pick(object, ['a', 'c']);
* // => { 'a': 1, 'c': 3 }
*/
var pick = flatRest(function(object, paths) {
	return object == null ? {} : basePick(object, paths);
});
//#endregion
//#region node_modules/lodash-es/_baseRange.js
var nativeCeil = Math.ceil, nativeMax = Math.max;
/**
* The base implementation of `_.range` and `_.rangeRight` which doesn't
* coerce arguments.
*
* @private
* @param {number} start The start of the range.
* @param {number} end The end of the range.
* @param {number} step The value to increment or decrement by.
* @param {boolean} [fromRight] Specify iterating from right to left.
* @returns {Array} Returns the range of numbers.
*/
function baseRange(start, end, step, fromRight) {
	var index = -1, length = nativeMax(nativeCeil((end - start) / (step || 1)), 0), result = Array(length);
	while (length--) {
		result[fromRight ? length : ++index] = start;
		start += step;
	}
	return result;
}
//#endregion
//#region node_modules/lodash-es/_createRange.js
/**
* Creates a `_.range` or `_.rangeRight` function.
*
* @private
* @param {boolean} [fromRight] Specify iterating from right to left.
* @returns {Function} Returns the new range function.
*/
function createRange(fromRight) {
	return function(start, end, step) {
		if (step && typeof step != "number" && isIterateeCall(start, end, step)) end = step = void 0;
		start = toFinite(start);
		if (end === void 0) {
			end = start;
			start = 0;
		} else end = toFinite(end);
		step = step === void 0 ? start < end ? 1 : -1 : toFinite(step);
		return baseRange(start, end, step, fromRight);
	};
}
//#endregion
//#region node_modules/lodash-es/range.js
/**
* Creates an array of numbers (positive and/or negative) progressing from
* `start` up to, but not including, `end`. A step of `-1` is used if a negative
* `start` is specified without an `end` or `step`. If `end` is not specified,
* it's set to `start` with `start` then set to `0`.
*
* **Note:** JavaScript follows the IEEE-754 standard for resolving
* floating-point values which can produce unexpected results.
*
* @static
* @since 0.1.0
* @memberOf _
* @category Util
* @param {number} [start=0] The start of the range.
* @param {number} end The end of the range.
* @param {number} [step=1] The value to increment or decrement by.
* @returns {Array} Returns the range of numbers.
* @see _.inRange, _.rangeRight
* @example
*
* _.range(4);
* // => [0, 1, 2, 3]
*
* _.range(-4);
* // => [0, -1, -2, -3]
*
* _.range(1, 5);
* // => [1, 2, 3, 4]
*
* _.range(0, 20, 5);
* // => [0, 5, 10, 15]
*
* _.range(0, -4, -1);
* // => [0, -1, -2, -3]
*
* _.range(1, 4, 0);
* // => [1, 1, 1]
*
* _.range(0);
* // => []
*/
var range = createRange();
//#endregion
//#region node_modules/lodash-es/size.js
/** `Object#toString` result references. */
var mapTag = "[object Map]", setTag = "[object Set]";
/**
* Gets the size of `collection` by returning its length for array-like
* values or the number of own enumerable string keyed properties for objects.
*
* @static
* @memberOf _
* @since 0.1.0
* @category Collection
* @param {Array|Object|string} collection The collection to inspect.
* @returns {number} Returns the collection size.
* @example
*
* _.size([1, 2, 3]);
* // => 3
*
* _.size({ 'a': 1, 'b': 2 });
* // => 2
*
* _.size('pebbles');
* // => 7
*/
function size(collection) {
	if (collection == null) return 0;
	if (isArrayLike(collection)) return isString(collection) ? stringSize(collection) : collection.length;
	var tag = _getTag_default(collection);
	if (tag == mapTag || tag == setTag) return collection.size;
	return baseKeys(collection).length;
}
//#endregion
//#region node_modules/lodash-es/sortBy.js
/**
* Creates an array of elements, sorted in ascending order by the results of
* running each element in a collection thru each iteratee. This method
* performs a stable sort, that is, it preserves the original sort order of
* equal elements. The iteratees are invoked with one argument: (value).
*
* @static
* @memberOf _
* @since 0.1.0
* @category Collection
* @param {Array|Object} collection The collection to iterate over.
* @param {...(Function|Function[])} [iteratees=[_.identity]]
*  The iteratees to sort by.
* @returns {Array} Returns the new sorted array.
* @example
*
* var users = [
*   { 'user': 'fred',   'age': 48 },
*   { 'user': 'barney', 'age': 36 },
*   { 'user': 'fred',   'age': 30 },
*   { 'user': 'barney', 'age': 34 }
* ];
*
* _.sortBy(users, [function(o) { return o.user; }]);
* // => objects for [['barney', 36], ['barney', 34], ['fred', 48], ['fred', 30]]
*
* _.sortBy(users, ['user', 'age']);
* // => objects for [['barney', 34], ['barney', 36], ['fred', 30], ['fred', 48]]
*/
var sortBy = baseRest(function(collection, iteratees) {
	if (collection == null) return [];
	var length = iteratees.length;
	if (length > 1 && isIterateeCall(collection, iteratees[0], iteratees[1])) iteratees = [];
	else if (length > 2 && isIterateeCall(iteratees[0], iteratees[1], iteratees[2])) iteratees = [iteratees[0]];
	return baseOrderBy(collection, baseFlatten(iteratees, 1), []);
});
//#endregion
//#region node_modules/lodash-es/union.js
/**
* Creates an array of unique values, in order, from all given arrays using
* [`SameValueZero`](http://ecma-international.org/ecma-262/7.0/#sec-samevaluezero)
* for equality comparisons.
*
* @static
* @memberOf _
* @since 0.1.0
* @category Array
* @param {...Array} [arrays] The arrays to inspect.
* @returns {Array} Returns the new array of combined values.
* @example
*
* _.union([2], [1, 2]);
* // => [2, 1]
*/
var union = baseRest(function(arrays) {
	return baseUniq(baseFlatten(arrays, 1, isArrayLikeObject, true));
});
//#endregion
//#region node_modules/lodash-es/uniqueId.js
/** Used to generate unique IDs. */
var idCounter = 0;
/**
* Generates a unique ID. If `prefix` is given, the ID is appended to it.
*
* @static
* @since 0.1.0
* @memberOf _
* @category Util
* @param {string} [prefix=''] The value to prefix the ID with.
* @returns {string} Returns the unique ID.
* @example
*
* _.uniqueId('contact_');
* // => 'contact_104'
*
* _.uniqueId();
* // => '105'
*/
function uniqueId(prefix) {
	var id = ++idCounter;
	return toString(prefix) + id;
}
//#endregion
//#region node_modules/lodash-es/_baseZipObject.js
/**
* This base implementation of `_.zipObject` which assigns values using `assignFunc`.
*
* @private
* @param {Array} props The property identifiers.
* @param {Array} values The property values.
* @param {Function} assignFunc The function to assign values.
* @returns {Object} Returns the new object.
*/
function baseZipObject(props, values, assignFunc) {
	var index = -1, length = props.length, valsLength = values.length, result = {};
	while (++index < length) {
		var value = index < valsLength ? values[index] : void 0;
		assignFunc(result, props[index], value);
	}
	return result;
}
//#endregion
//#region node_modules/lodash-es/zipObject.js
/**
* This method is like `_.fromPairs` except that it accepts two arrays,
* one of property identifiers and one of corresponding values.
*
* @static
* @memberOf _
* @since 0.4.0
* @category Array
* @param {Array} [props=[]] The property identifiers.
* @param {Array} [values=[]] The property values.
* @returns {Object} Returns the new object.
* @example
*
* _.zipObject(['a', 'b'], [1, 2]);
* // => { 'a': 1, 'b': 2 }
*/
function zipObject(props, values) {
	return baseZipObject(props || [], values || [], assignValue);
}
//#endregion
//#region node_modules/dagre-d3-es/src/graphlib/graph.js
var DEFAULT_EDGE_NAME = "\0";
var GRAPH_NODE = "\0";
var EDGE_KEY_DELIM = "";
/**
* @typedef {string} NodeID ID of a node.
*/
/**
* @typedef {`${string}${typeof EDGE_KEY_DELIM}${string}${typeof EDGE_KEY_DELIM}${string}`} EdgeID ID of an edge.
* @internal - All public APIs use {@link EdgeObj} instead to refer to edges.
*/
/**
* @typedef {object} EdgeObj
* @property {NodeID} v the id of the source or tail node of an edge
* @property {NodeID} w the id of the target or head node of an edge
* @property {string | number} [name] Name of the edge. Needed to uniquely identify
* multiple edges between the same pair of nodes in a multigraph.
*/
/**
* @template {unknown} T
* @typedef {T[] | Record<any, T>} Collection
* Lodash object that can be iterated over with `_.each`.
*
* Beware, objects with `.length` are treated as arrays, see
* https://lodash.com/docs/4.17.15#forEach
*/
/**
* @typedef {object} GraphOptions
* @property {boolean | undefined} [directed] - set to `true` to get a
* directed graph and `false` to get an undirected graph.
* An undirected graph does not treat the order of nodes in an edge as
* significant.
* In other words, `g.edge("a", "b") === g.edge("b", "a")` for
* an undirected graph.
* Default: `true`
* @property {boolean | undefined} [multigraph] - set to `true` to allow a
* graph to have multiple edges between the same pair of nodes.
* Default: `false`.
* @property {boolean | undefined} [compound] - set to `true` to allow a
* graph to have compound nodes - nodes which can be the parent of other
* nodes.
* Default: `false`.
*/
/**
* Graphlib has a single graph type: {@link Graph}. To create a new instance:
*
* ```js
* var g = new Graph();
* ```
*
* By default this will create a directed graph that does not allow multi-edges
* or compound nodes.
* The following options can be used when constructing a new graph:
*
* * {@link GraphOptions#directed}: set to `true` to get a directed graph and `false` to get an
*   undirected graph.
*   An undirected graph does not treat the order of nodes in an edge as
*   significant. In other words,
*   `g.edge("a", "b") === g.edge("b", "a")` for an undirected graph.
*   Default: `true`.
* * {@link GraphOptions#multigraph}: set to `true` to allow a graph to have multiple edges
*   between the same pair of nodes. Default: `false`.
* * {@link GraphOptions#compound}: set to `true` to allow a graph to have compound nodes -
*   nodes which can be the parent of other nodes. Default: `false`.
*
* To set the options, pass in an options object to the `Graph` constructor.
* For example, to create a directed compound multigraph:
*
* ```js
* var g = new Graph({ directed: true, compound: true, multigraph: true });
* ```
*
* ### Node and Edge Representation
*
* In graphlib, a node is represented by a user-supplied String id.
* All node related functions use this String id as a way to uniquely identify
* the node. Here is an example of interacting with nodes:
*
* ```js
* var g = new Graph();
* g.setNode("my-id", "my-label");
* g.node("my-id"); // returns "my-label"
* ```
*
* Edges in graphlib are identified by the nodes they connect. For example:
*
* ```js
* var g = new Graph();
* g.setEdge("source", "target", "my-label");
* g.edge("source", "target"); // returns "my-label"
* ```
*
* However, we need a way to uniquely identify an edge in a single object for
* various edge queries (e.g. {@link Graph#outEdges}).
* We use {@link EdgeObj}s for this purpose.
* They consist of the following properties:
*
* * {@link EdgeObj#v}: the id of the source or tail node of an edge
* * {@link EdgeObj#w}: the id of the target or head node of an edge
* * {@link EdgeObj#name} (optional): the name that uniquely identifies a multiedge.
*
* Any edge function that takes an edge id will also work with an {@link EdgeObj}. For example:
*
* ```js
* var g = new Graph();
* g.setEdge("source", "target", "my-label");
* g.edge({ v: "source", w: "target" }); // returns "my-label"
* ```
*
* ### Multigraphs
*
* A [multigraph](https://en.wikipedia.org/wiki/Multigraph) is a graph that can
* have more than one edge between the same pair of nodes.
* By default graphlib graphs are not multigraphs, but a multigraph can be
* constructed by setting the {@link GraphOptions#multigraph} property to true:
*
* ```js
* var g = new Graph({ multigraph: true });
* ```
*
* With multiple edges between two nodes we need some way to uniquely identify
* each edge. We call this the {@link EdgeObj#name} property.
* Here's an example of creating a couple of edges between the same nodes:
*
* ```js
* var g = new Graph({ multigraph: true });
* g.setEdge("a", "b", "edge1-label", "edge1");
* g.setEdge("a", "b", "edge2-label", "edge2");
* g.edge("a", "b", "edge1"); // returns "edge1-label"
* g.edge("a", "b", "edge2"); // returns "edge2-label"
* g.edges(); // returns [{ v: "a", w: "b", name: "edge1" },
*            //          { v: "a", w: "b", name: "edge2" }]
* ```
*
* A multigraph still allows an edge with no name to be created:
*
* ```js
* var g = new Graph({ multigraph: true });
* g.setEdge("a", "b", "my-label");
* g.edge({ v: "a", w: "b" }); // returns "my-label"
* ```
*
* ### Compound Graphs
*
* A compound graph is one where a node can be the parent of other nodes.
* The child nodes form a "subgraph".
* Here's an example of constructing and interacting with a compound graph:
*
* ```js
* var g = new Graph({ compound: true });
* g.setParent("a", "parent");
* g.setParent("b", "parent");
* g.parent("a");      // returns "parent"
* g.parent("b");      // returns "parent"
* g.parent("parent"); // returns undefined
* ```
*
* ### Default Labels
*
* When a node or edge is created without a label, a default label can be assigned.
* See {@link setDefaultNodeLabel} and {@link setDefaultEdgeLabel}.
*
* @template [GraphLabel=any] - Label of the graph.
* @template [NodeLabel=any] - Label of a node.
* Even though this is a "label", this could be any type that the user requires
* (and may need to be an object for some layout/ranking algorithms in dagre).
* @template [EdgeLabel=any] - Label of an edge.
* Even though this is a "label", this could be any type that the user requires,
* (and may need to be a object for ranking in dagre).
*/
var Graph = class {
	/**
	* @param {GraphOptions} [opts] - Graph options.
	*/
	constructor(opts = {}) {
		/**
		* @type {boolean}
		* @private
		*/
		this._isDirected = Object.prototype.hasOwnProperty.call(opts, "directed") ? opts.directed : true;
		/**
		* @type {boolean}
		* @private
		*/
		this._isMultigraph = Object.prototype.hasOwnProperty.call(opts, "multigraph") ? opts.multigraph : false;
		/**
		* @type {boolean}
		* @private
		*/
		this._isCompound = Object.prototype.hasOwnProperty.call(opts, "compound") ? opts.compound : false;
		/**
		* @type {GraphLabel | undefined}
		* Label for the graph itself
		*/
		this._label = void 0;
		/**
		* Default label to be set when creating a new node.
		*
		* @private
		* @type {(v: NodeID | number) => NodeLabel}
		*/
		this._defaultNodeLabelFn = constant(void 0);
		/**
		* Default label to be set when creating a new edge
		*
		* @private
		* @type {(v: NodeID, w: NodeID, name: string | undefined) => EdgeLabel}
		*/
		this._defaultEdgeLabelFn = constant(void 0);
		/**
		* @type {Record<NodeID, NodeLabel>}
		* @private
		*
		* v -> label
		*/
		this._nodes = {};
		if (this._isCompound) {
			/**
			* @type {Record<NodeID, NodeID>}
			* @private
			* v -> parent
			*/
			this._parent = {};
			/**
			* @type {Record<NodeID, Record<NodeID, true>>}
			* @private
			* v -> children
			*/
			this._children = {};
			this._children[GRAPH_NODE] = {};
		}
		/**
		* @type {Record<NodeID, Record<EdgeID, EdgeObj>>}
		* @private
		* v -> edgeObj
		*/
		this._in = {};
		/**
		* @type {Record<NodeID, Record<NodeID, number>>}
		* @private
		* u -> v -> Number
		*/
		this._preds = {};
		/**
		* @type {Record<NodeID, Record<EdgeID, EdgeObj>>}
		* @private
		* v -> edgeObj
		*/
		this._out = {};
		/**
		* @type {Record<NodeID, Record<NodeID, number>>}
		* @private
		* v -> w -> Number
		*/
		this._sucs = {};
		/**
		* @type {Record<EdgeID, EdgeObj>}
		* @private
		* e -> edgeObj
		*/
		this._edgeObjs = {};
		/**
		* @type {Record<EdgeID, EdgeLabel>}
		* @private
		* e -> label
		*/
		this._edgeLabels = {};
	}
	/**
	*
	* @returns {boolean} `true` if the graph is [directed](https://en.wikipedia.org/wiki/Directed_graph).
	* A directed graph treats the order of nodes in an edge as significant whereas an
	* [undirected](https://en.wikipedia.org/wiki/Graph_(mathematics)#Undirected_graph)
	* graph does not.
	* This example demonstrates the difference:
	*
	* @example
	*
	* ```js
	* var directed = new Graph({ directed: true });
	* directed.setEdge("a", "b", "my-label");
	* directed.edge("a", "b"); // returns "my-label"
	* directed.edge("b", "a"); // returns undefined
	*
	* var undirected = new Graph({ directed: false });
	* undirected.setEdge("a", "b", "my-label");
	* undirected.edge("a", "b"); // returns "my-label"
	* undirected.edge("b", "a"); // returns "my-label"
	* ```
	*/
	isDirected() {
		return this._isDirected;
	}
	/**
	* @returns {boolean} `true` if the graph is a multigraph.
	*/
	isMultigraph() {
		return this._isMultigraph;
	}
	/**
	* @returns {boolean} `true` if the graph is compound.
	*/
	isCompound() {
		return this._isCompound;
	}
	/**
	* Sets the label for the graph to `label`.
	*
	* @param {GraphLabel} label - Label for the graph.
	* @returns {this}
	*/
	setGraph(label) {
		this._label = label;
		return this;
	}
	/**
	* @returns {GraphLabel | undefined} the currently assigned label for the graph.
	* If no label has been assigned, returns `undefined`.
	*
	* @example
	*
	* ```js
	* var g = new Graph();
	* g.graph(); // returns undefined
	* g.setGraph("graph-label");
	*  g.graph(); // returns "graph-label"
	* ```
	*/
	graph() {
		return this._label;
	}
	/**
	* Sets a new default value that is assigned to nodes that are created without
	* a label.
	*
	* @param {typeof this._defaultNodeLabelFn | NodeLabel} newDefault - If a function,
	* it is called with the id of the node being created.
	* Otherwise, it is assigned as the label directly.
	* @returns {this}
	*/
	setDefaultNodeLabel(newDefault) {
		if (!isFunction(newDefault)) newDefault = constant(newDefault);
		this._defaultNodeLabelFn = newDefault;
		return this;
	}
	/**
	* @returns {number} the number of nodes in the graph.
	*/
	nodeCount() {
		return this._nodeCount;
	}
	/**
	* @returns {NodeID[]} the ids of the nodes in the graph.
	*
	* @remarks
	* Use {@link node()} to get the label for each node.
	* Takes `O(|V|)` time.
	*/
	nodes() {
		return keys(this._nodes);
	}
	/**
	* @returns {NodeID[]} those nodes in the graph that have no in-edges.
	* @remarks Takes `O(|V|)` time.
	*/
	sources() {
		var self = this;
		return filter(this.nodes(), function(v) {
			return isEmpty(self._in[v]);
		});
	}
	/**
	* @returns {NodeID[]} those nodes in the graph that have no out-edges.
	* @remarks Takes `O(|V|)` time.
	*/
	sinks() {
		var self = this;
		return filter(this.nodes(), function(v) {
			return isEmpty(self._out[v]);
		});
	}
	/**
	* Invokes setNode method for each node in `vs` list.
	*
	* @param {Collection<NodeID | number>} vs - List of node IDs to create/set.
	* @param {NodeLabel} [value] - If set, update all nodes with this value.
	* @returns {this}
	* @remarks Complexity: O(|names|).
	*/
	setNodes(vs, value) {
		var args = arguments;
		var self = this;
		forEach(vs, function(v) {
			if (args.length > 1) self.setNode(v, value);
			else self.setNode(v);
		});
		return this;
	}
	/**
	* Creates or updates the value for the node `v` in the graph.
	*
	* @param {NodeID | number} v - ID of the node to create/set.
	* @param {NodeLabel} [value] - If supplied, it is set as the value for the node.
	* If not supplied and the node was created by this call then
	* {@link setDefaultNodeLabel} will be used to set the node's value.
	* @returns {this} the graph, allowing this to be chained with other functions.
	* @remarks Takes `O(1)` time.
	*/
	setNode(v, value) {
		if (Object.prototype.hasOwnProperty.call(this._nodes, v)) {
			if (arguments.length > 1) this._nodes[v] = value;
			return this;
		}
		this._nodes[v] = arguments.length > 1 ? value : this._defaultNodeLabelFn(v);
		if (this._isCompound) {
			this._parent[v] = GRAPH_NODE;
			this._children[v] = {};
			this._children[GRAPH_NODE][v] = true;
		}
		this._in[v] = {};
		this._preds[v] = {};
		this._out[v] = {};
		this._sucs[v] = {};
		++this._nodeCount;
		return this;
	}
	/**
	* Gets the label of node with specified name.
	*
	* @param {NodeID | number} v - Node ID.
	* @returns {NodeLabel | undefined} the label assigned to the node with the id `v`
	* if it is in the graph.
	* Otherwise returns `undefined`.
	* @remarks Takes `O(1)` time.
	*/
	node(v) {
		return this._nodes[v];
	}
	/**
	* Detects whether graph has a node with specified name or not.
	*
	* @param {NodeID | number} v - Node ID.
	* @returns {boolean} Returns `true` the graph has a node with the id.
	* @remarks Takes `O(1)` time.
	*/
	hasNode(v) {
		return Object.prototype.hasOwnProperty.call(this._nodes, v);
	}
	/**
	* Remove the node with the id `v` in the graph or do nothing if the node is
	* not in the graph.
	*
	* If the node was removed this function also removes any incident edges.
	*
	* @param {NodeID | number} v - Node ID to remove.
	* @returns {this} the graph, allowing this to be chained with other functions.
	* @remarks Takes `O(|E|)` time.
	*/
	removeNode(v) {
		if (Object.prototype.hasOwnProperty.call(this._nodes, v)) {
			var removeEdge = (e) => this.removeEdge(this._edgeObjs[e]);
			delete this._nodes[v];
			if (this._isCompound) {
				this._removeFromParentsChildList(v);
				delete this._parent[v];
				forEach(this.children(v), (child) => {
					this.setParent(child);
				});
				delete this._children[v];
			}
			forEach(keys(this._in[v]), removeEdge);
			delete this._in[v];
			delete this._preds[v];
			forEach(keys(this._out[v]), removeEdge);
			delete this._out[v];
			delete this._sucs[v];
			--this._nodeCount;
		}
		return this;
	}
	/**
	* Sets the parent for `v` to `parent` if it is defined or removes the parent
	* for `v` if `parent` is undefined.
	*
	* @param {NodeID | number} v - Node ID to set the parent for.
	* @param {NodeID | number} [parent] - Parent node ID. If not defined, removes the parent.
	* @returns {this} the graph, allowing this to be chained with other functions.
	* @throws if the graph is not compound.
	* @throws if setting the parent would create a cycle.
	* @remarks Takes `O(1)` time.
	*/
	setParent(v, parent) {
		if (!this._isCompound) throw new Error("Cannot set parent in a non-compound graph");
		if (isUndefined(parent)) parent = GRAPH_NODE;
		else {
			parent += "";
			for (var ancestor = parent; !isUndefined(ancestor); ancestor = this.parent(ancestor)) if (ancestor === v) throw new Error("Setting " + parent + " as parent of " + v + " would create a cycle");
			this.setNode(parent);
		}
		this.setNode(v);
		this._removeFromParentsChildList(v);
		this._parent[v] = parent;
		this._children[parent][v] = true;
		return this;
	}
	/**
	* @private
	* @param {NodeID | number} v - Node ID.
	*/
	_removeFromParentsChildList(v) {
		delete this._children[this._parent[v]][v];
	}
	/**
	* Get parent node for node `v`.
	*
	* @param {NodeID | number} v - Node ID.
	* @returns {NodeID | undefined} the node that is a parent of node `v`
	* or `undefined` if node `v` does not have a parent or is not a member of
	* the graph.
	* Always returns `undefined` for graphs that are not compound.
	* @remarks Takes `O(1)` time.
	*/
	parent(v) {
		if (this._isCompound) {
			var parent = this._parent[v];
			if (parent !== GRAPH_NODE) return parent;
		}
	}
	/**
	* Gets list of direct children of node v.
	*
	* @param {NodeID | number} [v] - Node ID. If not specified, gets nodes
	* with no parent (top-level nodes).
	* @returns {NodeID[] | undefined} all nodes that are children of node `v` or
	* `undefined` if node `v` is not in the graph.
	* Always returns `[]` for graphs that are not compound.
	* @remarks Takes `O(|V|)` time.
	*/
	children(v) {
		if (isUndefined(v)) v = GRAPH_NODE;
		if (this._isCompound) {
			var children = this._children[v];
			if (children) return keys(children);
		} else if (v === GRAPH_NODE) return this.nodes();
		else if (this.hasNode(v)) return [];
	}
	/**
	* @param {NodeID | number} v - Node ID.
	* @returns {NodeID[] | undefined} all nodes that are predecessors of the
	* specified node or `undefined` if node `v` is not in the graph.
	* @remarks
	* Behavior is undefined for undirected graphs - use {@link neighbors} instead.
	* Takes `O(|V|)` time.
	*/
	predecessors(v) {
		var predsV = this._preds[v];
		if (predsV) return keys(predsV);
	}
	/**
	* @param {NodeID | number} v - Node ID.
	* @returns {NodeID[] | undefined} all nodes that are successors of the
	* specified node or `undefined` if node `v` is not in the graph.
	* @remarks
	* Behavior is undefined for undirected graphs - use {@link neighbors} instead.
	* Takes `O(|V|)` time.
	*/
	successors(v) {
		var sucsV = this._sucs[v];
		if (sucsV) return keys(sucsV);
	}
	/**
	* @param {NodeID | number} v - Node ID.
	* @returns {NodeID[] | undefined} all nodes that are predecessors or
	* successors of the specified node
	* or `undefined` if node `v` is not in the graph.
	* @remarks Takes `O(|V|)` time.
	*/
	neighbors(v) {
		var preds = this.predecessors(v);
		if (preds) return union(preds, this.successors(v));
	}
	/**
	* @param {NodeID | number} v - Node ID.
	* @returns {boolean} True if the node is a leaf (has no successors), false otherwise.
	*/
	isLeaf(v) {
		var neighbors;
		if (this.isDirected()) neighbors = this.successors(v);
		else neighbors = this.neighbors(v);
		return neighbors.length === 0;
	}
	/**
	* Creates new graph with nodes filtered via `filter`.
	* Edges incident to rejected node
	* are also removed.
	* 
	* In case of compound graph, if parent is rejected by `filter`,
	* than all its children are rejected too.
	
	* @param {(v: NodeID) => boolean} filter - Function that returns `true` for nodes to keep.
	* @returns {Graph<GraphLabel, NodeLabel, EdgeLabel>} A new graph containing only the nodes for which `filter` returns `true`.
	* @remarks Average-case complexity: O(|E|+|V|).
	*/
	filterNodes(filter) {
		/**
		* @type {Graph<GraphLabel, NodeLabel, EdgeLabel>}
		*/
		var copy = new this.constructor({
			directed: this._isDirected,
			multigraph: this._isMultigraph,
			compound: this._isCompound
		});
		copy.setGraph(this.graph());
		var self = this;
		forEach(this._nodes, function(value, v) {
			if (filter(v)) copy.setNode(v, value);
		});
		forEach(this._edgeObjs, function(e) {
			if (copy.hasNode(e.v) && copy.hasNode(e.w)) copy.setEdge(e, self.edge(e));
		});
		var parents = {};
		function findParent(v) {
			var parent = self.parent(v);
			if (parent === void 0 || copy.hasNode(parent)) {
				parents[v] = parent;
				return parent;
			} else if (parent in parents) return parents[parent];
			else return findParent(parent);
		}
		if (this._isCompound) forEach(copy.nodes(), function(v) {
			copy.setParent(v, findParent(v));
		});
		return copy;
	}
	/**
	* Sets a new default value that is assigned to edges that are created without
	* a label.
	*
	* @param {typeof this._defaultEdgeLabelFn | EdgeLabel} newDefault - If a function,
	* it is called with the parameters `(v, w, name)`.
	* Otherwise, it is assigned as the label directly.
	* @returns {this}
	*/
	setDefaultEdgeLabel(newDefault) {
		if (!isFunction(newDefault)) newDefault = constant(newDefault);
		this._defaultEdgeLabelFn = newDefault;
		return this;
	}
	/**
	* @returns {number} the number of edges in the graph.
	* @remarks Complexity: O(1).
	*/
	edgeCount() {
		return this._edgeCount;
	}
	/**
	* Gets edges of the graph.
	*
	* @returns {EdgeObj[]} the {@link EdgeObj} for each edge in the graph.
	*
	* @remarks
	* In case of compound graph subgraphs are not considered.
	* Use {@link edge()} to get the label for each edge.
	* Takes `O(|E|)` time.
	*/
	edges() {
		return values(this._edgeObjs);
	}
	/**
	* Establish an edges path over the nodes in nodes list.
	*
	* If some edge is already exists, it will update its label, otherwise it will
	* create an edge between pair of nodes with label provided or default label
	* if no label provided.
	*
	* @param {Collection<NodeID>} vs - List of node IDs to create edges between.
	* @param {EdgeLabel} [value] - If set, update all edges with this value.
	* @returns {this}
	* @remarks Complexity: O(|nodes|).
	*/
	setPath(vs, value) {
		var self = this;
		var args = arguments;
		reduce(vs, function(v, w) {
			if (args.length > 1) self.setEdge(v, w, value);
			else self.setEdge(v, w);
			return w;
		});
		return this;
	}
	/**
	* Creates or updates the label for the edge (`v`, `w`) with the optionally
	* supplied `name`.
	*
	* @overload
	* @param {EdgeObj} arg0 - Edge object.
	* @param {EdgeLabel} [value] - If supplied, it is set as the label for the edge.
	* If not supplied and the edge was created by this call then
	* {@link setDefaultEdgeLabel} will be used to assign the edge's label.
	* @returns {this} the graph, allowing this to be chained with other functions.
	* @remarks Takes `O(1)` time.
	*/
	/**
	* Creates or updates the label for the edge (`v`, `w`) with the optionally
	* supplied `name`.
	*
	* @overload
	* @param {NodeID | number} v - Source node ID. Number values will be coerced to strings.
	* @param {NodeID | number} w - Target node ID. Number values will be coerced to strings.
	* @param {EdgeLabel} [value] - If supplied, it is set as the label for the edge.
	* If not supplied and the edge was created by this call then
	* {@link setDefaultEdgeLabel} will be used to assign the edge's label.
	* @param {string | number} [name] - Edge name. Only useful with multigraphs.
	* @returns {this} the graph, allowing this to be chained with other functions.
	* @remarks Takes `O(1)` time.
	*/
	setEdge() {
		var v, w, name, value;
		var valueSpecified = false;
		var arg0 = arguments[0];
		if (typeof arg0 === "object" && arg0 !== null && "v" in arg0) {
			v = arg0.v;
			w = arg0.w;
			name = arg0.name;
			if (arguments.length === 2) {
				value = arguments[1];
				valueSpecified = true;
			}
		} else {
			v = arg0;
			w = arguments[1];
			name = arguments[3];
			if (arguments.length > 2) {
				value = arguments[2];
				valueSpecified = true;
			}
		}
		v = "" + v;
		w = "" + w;
		if (!isUndefined(name)) name = "" + name;
		var e = edgeArgsToId(this._isDirected, v, w, name);
		if (Object.prototype.hasOwnProperty.call(this._edgeLabels, e)) {
			if (valueSpecified) this._edgeLabels[e] = value;
			return this;
		}
		if (!isUndefined(name) && !this._isMultigraph) throw new Error("Cannot set a named edge when isMultigraph = false");
		this.setNode(v);
		this.setNode(w);
		this._edgeLabels[e] = valueSpecified ? value : this._defaultEdgeLabelFn(v, w, name);
		var edgeObj = edgeArgsToObj(this._isDirected, v, w, name);
		v = edgeObj.v;
		w = edgeObj.w;
		Object.freeze(edgeObj);
		this._edgeObjs[e] = edgeObj;
		incrementOrInitEntry(this._preds[w], v);
		incrementOrInitEntry(this._sucs[v], w);
		this._in[w][e] = edgeObj;
		this._out[v][e] = edgeObj;
		this._edgeCount++;
		return this;
	}
	/**
	* Gets the label for the specified edge.
	*
	* @overload
	* @param {EdgeObj} v - Edge object.
	* @returns {EdgeLabel | undefined} the label for the edge (`v`, `w`) if the
	* graph has an edge between `v` and `w` with the optional `name`.
	* Returned `undefined` if there is no such edge in the graph.
	* @remarks
	* `v` and `w` can be interchanged for undirected graphs.
	* Takes `O(1)` time.
	*/
	/**
	* Gets the label for the specified edge.
	*
	* @overload
	* @param {NodeID | number} v - Source node ID.
	* @param {NodeID | number} w - Target node ID.
	* @param {string | number} [name] - Edge name. Only useful with multigraphs.
	* @returns {EdgeLabel | undefined} the label for the edge (`v`, `w`) if the
	* graph has an edge between `v` and `w` with the optional `name`.
	* Returned `undefined` if there is no such edge in the graph.
	* @remarks
	* `v` and `w` can be interchanged for undirected graphs.
	* Takes `O(1)` time.
	*/
	edge(v, w, name) {
		var e = arguments.length === 1 ? edgeObjToId(this._isDirected, arguments[0]) : edgeArgsToId(this._isDirected, v, w, name);
		return this._edgeLabels[e];
	}
	/**
	* Detects whether the graph contains specified edge or not.
	*
	* @overload
	* @param {EdgeObj} v - Edge object.
	* @returns {boolean} `true` if the graph has an edge between `v` and `w`
	* with the optional `name`.
	* @remarks
	* `v` and `w` can be interchanged for undirected graphs.
	* No subgraphs are considered.
	* Takes `O(1)` time.
	*/
	/**
	* Detects whether the graph contains specified edge or not.
	*
	* @overload
	* @param {NodeID | number} v - Source node ID.
	* @param {NodeID | number} w - Target node ID.
	* @param {string | number} [name] - Edge name. Only useful with multigraphs.
	* @returns {boolean} `true` if the graph has an edge between `v` and `w`
	* with the optional `name`.
	* @remarks
	* `v` and `w` can be interchanged for undirected graphs.
	* No subgraphs are considered.
	* Takes `O(1)` time.
	*/
	hasEdge(v, w, name) {
		var e = arguments.length === 1 ? edgeObjToId(this._isDirected, arguments[0]) : edgeArgsToId(this._isDirected, v, w, name);
		return Object.prototype.hasOwnProperty.call(this._edgeLabels, e);
	}
	/**
	* Removes the edge (`v`, `w`) if the graph has an edge between `v` and `w`
	* with the optional `name`. If not this function does nothing.
	*
	* @overload
	* @param {EdgeObj} v - Edge object.
	* @returns {this}
	* @remarks
	* `v` and `w` can be interchanged for undirected graphs.
	* No subgraphs are considered.
	* Takes `O(1)` time.
	*/
	/**
	* Removes the edge (`v`, `w`) if the graph has an edge between `v` and `w`
	* with the optional `name`. If not this function does nothing.
	*
	* @overload
	* @param {NodeID | number} v - Source node ID.
	* @param {NodeID | number} w - Target node ID.
	* @param {string | number} [name] - Edge name. Only useful with multigraphs.
	* @returns {this}
	* @remarks
	* `v` and `w` can be interchanged for undirected graphs.
	* Takes `O(1)` time.
	*/
	removeEdge(v, w, name) {
		var e = arguments.length === 1 ? edgeObjToId(this._isDirected, arguments[0]) : edgeArgsToId(this._isDirected, v, w, name);
		var edge = this._edgeObjs[e];
		if (edge) {
			v = edge.v;
			w = edge.w;
			delete this._edgeLabels[e];
			delete this._edgeObjs[e];
			decrementOrRemoveEntry(this._preds[w], v);
			decrementOrRemoveEntry(this._sucs[v], w);
			delete this._in[w][e];
			delete this._out[v][e];
			this._edgeCount--;
		}
		return this;
	}
	/**
	* @param {NodeID | number} v - Target node ID.
	* @param {NodeID | number} [u] - Optionally filters edges down to just those
	* coming from node `u`.
	* @returns {EdgeObj[] | undefined} all edges that point to the node `v`.
	* Returns `undefined` if node `v` is not in the graph.
	* @remarks
	* Behavior is undefined for undirected graphs - use {@link nodeEdges} instead.
	* Takes `O(|E|)` time.
	*/
	inEdges(v, u) {
		var inV = this._in[v];
		if (inV) {
			var edges = values(inV);
			if (!u) return edges;
			return filter(edges, function(edge) {
				return edge.v === u;
			});
		}
	}
	/**
	* @param {NodeID | number} v - Target node ID.
	* @param {NodeID | number} [w] - Optionally filters edges down to just those
	* that point to `w`.
	* @returns {EdgeObj[] | undefined} all edges that point to the node `v`.
	* Returns `undefined` if node `v` is not in the graph.
	* @remarks
	* Behavior is undefined for undirected graphs - use {@link nodeEdges} instead.
	* Takes `O(|E|)` time.
	*/
	outEdges(v, w) {
		var outV = this._out[v];
		if (outV) {
			var edges = values(outV);
			if (!w) return edges;
			return filter(edges, function(edge) {
				return edge.w === w;
			});
		}
	}
	/**
	* @param {NodeID | number} v - Target Node ID.
	* @param {NodeID | number} [w] - If set, filters those edges down to just
	* those between nodes `v` and `w` regardless of direction
	* @returns {EdgeObj[] | undefined} all edges to or from node `v` regardless
	* of direction. Returns `undefined` if node `v` is not in the graph.
	* @remarks Takes `O(|E|)` time.
	*/
	nodeEdges(v, w) {
		var inEdges = this.inEdges(v, w);
		if (inEdges) return inEdges.concat(this.outEdges(v, w));
	}
};
Graph.prototype._nodeCount = 0;
Graph.prototype._edgeCount = 0;
/**
* @param {Record<NodeID, number>} map - Object mapping node IDs to counts.
* @param {NodeID | number} k - Node ID.
*/
function incrementOrInitEntry(map, k) {
	if (map[k]) map[k]++;
	else map[k] = 1;
}
/**
* @param {Record<NodeID, number>} map - Object mapping node IDs to counts.
* @param {NodeID | number} k - Node ID.
*/
function decrementOrRemoveEntry(map, k) {
	if (!--map[k]) delete map[k];
}
/**
* @param {boolean} isDirected - If `false`, sorts v and w to ensure a consistent ID.
* @param {EdgeObj['v'] | number} v_ - Source node ID.
* @param {EdgeObj['w'] | number} w_ - Target node ID.
* @param {EdgeObj['name']} [name] - Edge name (for multiple edges between the same nodes).
* @returns {EdgeID} Unique ID for the edge.
*/
function edgeArgsToId(isDirected, v_, w_, name) {
	var v = "" + v_;
	var w = "" + w_;
	if (!isDirected && v > w) {
		var tmp = v;
		v = w;
		w = tmp;
	}
	return v + EDGE_KEY_DELIM + w + EDGE_KEY_DELIM + (isUndefined(name) ? DEFAULT_EDGE_NAME : name);
}
/**
* @param {boolean} isDirected - If `false`, sorts v and w to ensure a consistent ID.
* @param {EdgeObj['v'] | number} v_ - Source node ID.
* @param {EdgeObj['w'] | number} w_ - Target node ID.
* @param {EdgeObj['name']} [name] - Edge name (for multiple edges between the same nodes).
* @returns {EdgeObj}
*/
function edgeArgsToObj(isDirected, v_, w_, name) {
	var v = "" + v_;
	var w = "" + w_;
	if (!isDirected && v > w) {
		var tmp = v;
		v = w;
		w = tmp;
	}
	var edgeObj = {
		v,
		w
	};
	if (name) edgeObj.name = name;
	return edgeObj;
}
/**
* @param {boolean} isDirected - If `false`, sorts v and w to ensure a consistent ID.
* @param {EdgeObj} edgeObj - Edge object.
* @returns {EdgeID} Unique ID for the edge.
*/
function edgeObjToId(isDirected, edgeObj) {
	return edgeArgsToId(isDirected, edgeObj.v, edgeObj.w, edgeObj.name);
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/data/list.js
var List = class {
	constructor() {
		var sentinel = {};
		sentinel._next = sentinel._prev = sentinel;
		this._sentinel = sentinel;
	}
	dequeue() {
		var sentinel = this._sentinel;
		var entry = sentinel._prev;
		if (entry !== sentinel) {
			unlink(entry);
			return entry;
		}
	}
	enqueue(entry) {
		var sentinel = this._sentinel;
		if (entry._prev && entry._next) unlink(entry);
		entry._next = sentinel._next;
		sentinel._next._prev = entry;
		sentinel._next = entry;
		entry._prev = sentinel;
	}
	toString() {
		var strs = [];
		var sentinel = this._sentinel;
		var curr = sentinel._prev;
		while (curr !== sentinel) {
			strs.push(JSON.stringify(curr, filterOutLinks));
			curr = curr._prev;
		}
		return "[" + strs.join(", ") + "]";
	}
};
function unlink(entry) {
	entry._prev._next = entry._next;
	entry._next._prev = entry._prev;
	delete entry._next;
	delete entry._prev;
}
function filterOutLinks(k, v) {
	if (k !== "_next" && k !== "_prev") return v;
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/greedy-fas.js
var DEFAULT_WEIGHT_FN = constant(1);
function greedyFAS(g, weightFn) {
	if (g.nodeCount() <= 1) return [];
	var state = buildState(g, weightFn || DEFAULT_WEIGHT_FN);
	return flatten(map(doGreedyFAS(state.graph, state.buckets, state.zeroIdx), function(e) {
		return g.outEdges(e.v, e.w);
	}));
}
function doGreedyFAS(g, buckets, zeroIdx) {
	var results = [];
	var sources = buckets[buckets.length - 1];
	var sinks = buckets[0];
	var entry;
	while (g.nodeCount()) {
		while (entry = sinks.dequeue()) removeNode(g, buckets, zeroIdx, entry);
		while (entry = sources.dequeue()) removeNode(g, buckets, zeroIdx, entry);
		if (g.nodeCount()) for (var i = buckets.length - 2; i > 0; --i) {
			entry = buckets[i].dequeue();
			if (entry) {
				results = results.concat(removeNode(g, buckets, zeroIdx, entry, true));
				break;
			}
		}
	}
	return results;
}
function removeNode(g, buckets, zeroIdx, entry, collectPredecessors) {
	var results = collectPredecessors ? [] : void 0;
	forEach(g.inEdges(entry.v), function(edge) {
		var weight = g.edge(edge);
		var uEntry = g.node(edge.v);
		if (collectPredecessors) results.push({
			v: edge.v,
			w: edge.w
		});
		uEntry.out -= weight;
		assignBucket(buckets, zeroIdx, uEntry);
	});
	forEach(g.outEdges(entry.v), function(edge) {
		var weight = g.edge(edge);
		var w = edge.w;
		var wEntry = g.node(w);
		wEntry["in"] -= weight;
		assignBucket(buckets, zeroIdx, wEntry);
	});
	g.removeNode(entry.v);
	return results;
}
function buildState(g, weightFn) {
	var fasGraph = new Graph();
	var maxIn = 0;
	var maxOut = 0;
	forEach(g.nodes(), function(v) {
		fasGraph.setNode(v, {
			v,
			in: 0,
			out: 0
		});
	});
	forEach(g.edges(), function(e) {
		var prevWeight = fasGraph.edge(e.v, e.w) || 0;
		var weight = weightFn(e);
		var edgeWeight = prevWeight + weight;
		fasGraph.setEdge(e.v, e.w, edgeWeight);
		maxOut = Math.max(maxOut, fasGraph.node(e.v).out += weight);
		maxIn = Math.max(maxIn, fasGraph.node(e.w)["in"] += weight);
	});
	var buckets = range(maxOut + maxIn + 3).map(function() {
		return new List();
	});
	var zeroIdx = maxIn + 1;
	forEach(fasGraph.nodes(), function(v) {
		assignBucket(buckets, zeroIdx, fasGraph.node(v));
	});
	return {
		graph: fasGraph,
		buckets,
		zeroIdx
	};
}
function assignBucket(buckets, zeroIdx, entry) {
	if (!entry.out) buckets[0].enqueue(entry);
	else if (!entry["in"]) buckets[buckets.length - 1].enqueue(entry);
	else buckets[entry.out - entry["in"] + zeroIdx].enqueue(entry);
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/acyclic.js
function run$2(g) {
	forEach(g.graph().acyclicer === "greedy" ? greedyFAS(g, weightFn(g)) : dfsFAS(g), function(e) {
		var label = g.edge(e);
		g.removeEdge(e);
		label.forwardName = e.name;
		label.reversed = true;
		g.setEdge(e.w, e.v, label, uniqueId("rev"));
	});
	function weightFn(g) {
		return function(e) {
			return g.edge(e).weight;
		};
	}
}
function dfsFAS(g) {
	var fas = [];
	var stack = {};
	var visited = {};
	function dfs(v) {
		if (Object.prototype.hasOwnProperty.call(visited, v)) return;
		visited[v] = true;
		stack[v] = true;
		forEach(g.outEdges(v), function(e) {
			if (Object.prototype.hasOwnProperty.call(stack, e.w)) fas.push(e);
			else dfs(e.w);
		});
		delete stack[v];
	}
	forEach(g.nodes(), dfs);
	return fas;
}
function undo$2(g) {
	forEach(g.edges(), function(e) {
		var label = g.edge(e);
		if (label.reversed) {
			g.removeEdge(e);
			var forwardName = label.forwardName;
			delete label.reversed;
			delete label.forwardName;
			g.setEdge(e.w, e.v, label, forwardName);
		}
	});
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/util.js
function addDummyNode(g, type, attrs, name) {
	var v;
	do
		v = uniqueId(name);
	while (g.hasNode(v));
	attrs.dummy = type;
	g.setNode(v, attrs);
	return v;
}
function simplify(g) {
	var simplified = new Graph().setGraph(g.graph());
	forEach(g.nodes(), function(v) {
		simplified.setNode(v, g.node(v));
	});
	forEach(g.edges(), function(e) {
		var simpleLabel = simplified.edge(e.v, e.w) || {
			weight: 0,
			minlen: 1
		};
		var label = g.edge(e);
		simplified.setEdge(e.v, e.w, {
			weight: simpleLabel.weight + label.weight,
			minlen: Math.max(simpleLabel.minlen, label.minlen)
		});
	});
	return simplified;
}
function asNonCompoundGraph(g) {
	var simplified = new Graph({ multigraph: g.isMultigraph() }).setGraph(g.graph());
	forEach(g.nodes(), function(v) {
		if (!g.children(v).length) simplified.setNode(v, g.node(v));
	});
	forEach(g.edges(), function(e) {
		simplified.setEdge(e, g.edge(e));
	});
	return simplified;
}
function intersectRect(rect, point) {
	var x = rect.x;
	var y = rect.y;
	var dx = point.x - x;
	var dy = point.y - y;
	var w = rect.width / 2;
	var h = rect.height / 2;
	if (!dx && !dy) throw new Error("Not possible to find intersection inside of the rectangle");
	var sx, sy;
	if (Math.abs(dy) * w > Math.abs(dx) * h) {
		if (dy < 0) h = -h;
		sx = h * dx / dy;
		sy = h;
	} else {
		if (dx < 0) w = -w;
		sx = w;
		sy = w * dy / dx;
	}
	return {
		x: x + sx,
		y: y + sy
	};
}
function buildLayerMatrix(g) {
	var layering = map(range(maxRank(g) + 1), function() {
		return [];
	});
	forEach(g.nodes(), function(v) {
		var node = g.node(v);
		var rank = node.rank;
		if (!isUndefined(rank)) layering[rank][node.order] = v;
	});
	return layering;
}
function normalizeRanks(g) {
	var min$1 = min(map(g.nodes(), function(v) {
		return g.node(v).rank;
	}));
	forEach(g.nodes(), function(v) {
		var node = g.node(v);
		if (has(node, "rank")) node.rank -= min$1;
	});
}
function removeEmptyRanks(g) {
	var offset = min(map(g.nodes(), function(v) {
		return g.node(v).rank;
	}));
	var layers = [];
	forEach(g.nodes(), function(v) {
		var rank = g.node(v).rank - offset;
		if (!layers[rank]) layers[rank] = [];
		layers[rank].push(v);
	});
	var delta = 0;
	var nodeRankFactor = g.graph().nodeRankFactor;
	forEach(layers, function(vs, i) {
		if (isUndefined(vs) && i % nodeRankFactor !== 0) --delta;
		else if (delta) forEach(vs, function(v) {
			g.node(v).rank += delta;
		});
	});
}
function addBorderNode$1(g, prefix, rank, order) {
	var node = {
		width: 0,
		height: 0
	};
	if (arguments.length >= 4) {
		node.rank = rank;
		node.order = order;
	}
	return addDummyNode(g, "border", node, prefix);
}
function maxRank(g) {
	return max(map(g.nodes(), function(v) {
		var rank = g.node(v).rank;
		if (!isUndefined(rank)) return rank;
	}));
}
function partition(collection, fn) {
	var result = {
		lhs: [],
		rhs: []
	};
	forEach(collection, function(value) {
		if (fn(value)) result.lhs.push(value);
		else result.rhs.push(value);
	});
	return result;
}
function time(name, fn) {
	var start = now();
	try {
		return fn();
	} finally {
		console.log(name + " time: " + (now() - start) + "ms");
	}
}
function notime(name, fn) {
	return fn();
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/add-border-segments.js
function addBorderSegments(g) {
	function dfs(v) {
		var children = g.children(v);
		var node = g.node(v);
		if (children.length) forEach(children, dfs);
		if (Object.prototype.hasOwnProperty.call(node, "minRank")) {
			node.borderLeft = [];
			node.borderRight = [];
			for (var rank = node.minRank, maxRank = node.maxRank + 1; rank < maxRank; ++rank) {
				addBorderNode(g, "borderLeft", "_bl", v, node, rank);
				addBorderNode(g, "borderRight", "_br", v, node, rank);
			}
		}
	}
	forEach(g.children(), dfs);
}
function addBorderNode(g, prop, prefix, sg, sgNode, rank) {
	var label = {
		width: 0,
		height: 0,
		rank,
		borderType: prop
	};
	var prev = sgNode[prop][rank - 1];
	var curr = addDummyNode(g, "border", label, prefix);
	sgNode[prop][rank] = curr;
	g.setParent(curr, sg);
	if (prev) g.setEdge(prev, curr, { weight: 1 });
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/coordinate-system.js
function adjust(g) {
	var rankDir = g.graph().rankdir.toLowerCase();
	if (rankDir === "lr" || rankDir === "rl") swapWidthHeight(g);
}
function undo$1(g) {
	var rankDir = g.graph().rankdir.toLowerCase();
	if (rankDir === "bt" || rankDir === "rl") reverseY(g);
	if (rankDir === "lr" || rankDir === "rl") {
		swapXY(g);
		swapWidthHeight(g);
	}
}
function swapWidthHeight(g) {
	forEach(g.nodes(), function(v) {
		swapWidthHeightOne(g.node(v));
	});
	forEach(g.edges(), function(e) {
		swapWidthHeightOne(g.edge(e));
	});
}
function swapWidthHeightOne(attrs) {
	var w = attrs.width;
	attrs.width = attrs.height;
	attrs.height = w;
}
function reverseY(g) {
	forEach(g.nodes(), function(v) {
		reverseYOne(g.node(v));
	});
	forEach(g.edges(), function(e) {
		var edge = g.edge(e);
		forEach(edge.points, reverseYOne);
		if (Object.prototype.hasOwnProperty.call(edge, "y")) reverseYOne(edge);
	});
}
function reverseYOne(attrs) {
	attrs.y = -attrs.y;
}
function swapXY(g) {
	forEach(g.nodes(), function(v) {
		swapXYOne(g.node(v));
	});
	forEach(g.edges(), function(e) {
		var edge = g.edge(e);
		forEach(edge.points, swapXYOne);
		if (Object.prototype.hasOwnProperty.call(edge, "x")) swapXYOne(edge);
	});
}
function swapXYOne(attrs) {
	var x = attrs.x;
	attrs.x = attrs.y;
	attrs.y = x;
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/normalize.js
/**
* TypeScript type imports:
*
* @import { Graph } from '../graphlib/graph.js';
*/
function run$1(g) {
	g.graph().dummyChains = [];
	forEach(g.edges(), function(edge) {
		normalizeEdge(g, edge);
	});
}
/**
* @param {Graph} g
*/
function normalizeEdge(g, e) {
	var v = e.v;
	var vRank = g.node(v).rank;
	var w = e.w;
	var wRank = g.node(w).rank;
	var name = e.name;
	var edgeLabel = g.edge(e);
	var labelRank = edgeLabel.labelRank;
	if (wRank === vRank + 1) return;
	g.removeEdge(e);
	/**
	* @typedef {Object} Attrs
	* @property {number} width
	* @property {number} height
	* @property {ReturnType<Graph["node"]>} edgeLabel
	* @property {any} edgeObj
	* @property {ReturnType<Graph["node"]>["rank"]} rank
	* @property {string} [dummy]
	* @property {ReturnType<Graph["node"]>["labelpos"]} [labelpos]
	*/
	/** @type {Attrs | undefined} */
	var attrs = void 0;
	var dummy, i;
	for (i = 0, ++vRank; vRank < wRank; ++i, ++vRank) {
		edgeLabel.points = [];
		attrs = {
			width: 0,
			height: 0,
			edgeLabel,
			edgeObj: e,
			rank: vRank
		};
		dummy = addDummyNode(g, "edge", attrs, "_d");
		if (vRank === labelRank) {
			attrs.width = edgeLabel.width;
			attrs.height = edgeLabel.height;
			attrs.dummy = "edge-label";
			attrs.labelpos = edgeLabel.labelpos;
		}
		g.setEdge(v, dummy, { weight: edgeLabel.weight }, name);
		if (i === 0) g.graph().dummyChains.push(dummy);
		v = dummy;
	}
	g.setEdge(v, w, { weight: edgeLabel.weight }, name);
}
function undo(g) {
	forEach(g.graph().dummyChains, function(v) {
		var node = g.node(v);
		var origLabel = node.edgeLabel;
		var w;
		g.setEdge(node.edgeObj, origLabel);
		while (node.dummy) {
			w = g.successors(v)[0];
			g.removeNode(v);
			origLabel.points.push({
				x: node.x,
				y: node.y
			});
			if (node.dummy === "edge-label") {
				origLabel.x = node.x;
				origLabel.y = node.y;
				origLabel.width = node.width;
				origLabel.height = node.height;
			}
			v = w;
			node = g.node(v);
		}
	});
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/rank/util.js
function longestPath(g) {
	var visited = {};
	function dfs(v) {
		var label = g.node(v);
		if (Object.prototype.hasOwnProperty.call(visited, v)) return label.rank;
		visited[v] = true;
		var rank = min(map(g.outEdges(v), function(e) {
			return dfs(e.w) - g.edge(e).minlen;
		}));
		if (rank === Number.POSITIVE_INFINITY || rank === void 0 || rank === null) rank = 0;
		return label.rank = rank;
	}
	forEach(g.sources(), dfs);
}
function slack(g, e) {
	return g.node(e.w).rank - g.node(e.v).rank - g.edge(e).minlen;
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/rank/feasible-tree.js
function feasibleTree(g) {
	var t = new Graph({ directed: false });
	var start = g.nodes()[0];
	var size = g.nodeCount();
	t.setNode(start, {});
	var edge, delta;
	while (tightTree(t, g) < size) {
		edge = findMinSlackEdge(t, g);
		delta = t.hasNode(edge.v) ? slack(g, edge) : -slack(g, edge);
		shiftRanks(t, g, delta);
	}
	return t;
}
function tightTree(t, g) {
	function dfs(v) {
		forEach(g.nodeEdges(v), function(e) {
			var edgeV = e.v, w = v === edgeV ? e.w : edgeV;
			if (!t.hasNode(w) && !slack(g, e)) {
				t.setNode(w, {});
				t.setEdge(v, w, {});
				dfs(w);
			}
		});
	}
	forEach(t.nodes(), dfs);
	return t.nodeCount();
}
function findMinSlackEdge(t, g) {
	return minBy(g.edges(), function(e) {
		if (t.hasNode(e.v) !== t.hasNode(e.w)) return slack(g, e);
	});
}
function shiftRanks(t, g, delta) {
	forEach(t.nodes(), function(v) {
		g.node(v).rank += delta;
	});
}
//#endregion
//#region node_modules/dagre-d3-es/src/graphlib/alg/topsort.js
topsort.CycleException = CycleException;
/**
* An implementation of [topological sorting](https://en.wikipedia.org/wiki/Topological_sorting).
*
* @remarks Takes `O(|V| + |E|)` time.
*
* @example
*
* ![](https://github.com/dagrejs/graphlib/wiki/images/topsort.png)
*
* ```js
* graphlib.alg.topsort(g)
* // [ '1', '2', '3', '4' ] or [ '1', '3', '2', '4' ]
* ```
*
* @param {Graph} g - The graph to sort.
* @returns {NodeID[]} an array of nodes
* such that for each edge `u -> v`, `u` appears before `v` in the array.
* @throws {CycleException} If the graph has a cycle so that it is impossible
* to generate a topological sort.
*/
function topsort(g) {
	/** @type {Record<NodeID, true>} */
	var visited = {};
	/** @type {Record<NodeID, true>} */
	var stack = {};
	/** @type {NodeID[]} */
	var results = [];
	/**
	* @param {NodeID} node - Node to recursively visit.
	*/
	function visit(node) {
		if (Object.prototype.hasOwnProperty.call(stack, node)) throw new CycleException();
		if (!Object.prototype.hasOwnProperty.call(visited, node)) {
			stack[node] = true;
			visited[node] = true;
			forEach(g.predecessors(node), visit);
			delete stack[node];
			results.push(node);
		}
	}
	forEach(g.sinks(), visit);
	if (size(visited) !== g.nodeCount()) throw new CycleException();
	return results;
}
/**
* @class
*/
function CycleException() {}
CycleException.prototype = /* @__PURE__ */ new Error();
//#endregion
//#region node_modules/dagre-d3-es/src/graphlib/alg/dfs.js
/**
* A helper that preforms a pre- or post-order traversal on the input graph
* and returns the nodes in the order they were visited. If the graph is
* undirected then this algorithm will navigate using neighbors. If the graph
* is directed then this algorithm will navigate using successors.
*
* @param {Graph} g - Input graph.
* @param {NodeID[] | NodeID} vs - Starting node or array of nodes.
* @param {'post' | 'pre'} order - The order to use. Must be one of "pre" or "post".
* @returns {NodeID[]} The nodes in the order they were visited.
*/
function dfs$1(g, vs, order) {
	if (!isArray(vs)) vs = [vs];
	/** @type {Parameters<typeof doDfs>[4]} */
	var navigation = (g.isDirected() ? g.successors : g.neighbors).bind(g);
	/** @type {Parameters<typeof doDfs>[5]} */
	var acc = [];
	/** @type {Parameters<typeof doDfs>[3]} */
	var visited = {};
	forEach(vs, function(v) {
		if (!g.hasNode(v)) throw new Error("Graph does not have node: " + v);
		doDfs(g, v, order === "post", visited, navigation, acc);
	});
	return acc;
}
/**
* @param {Graph} g - Input graph.
* @param {NodeID} v - The node to visit.
* @param {boolean} postorder - Whether to do postorder traversal.
* @param {Record<NodeID, true>} visited - Visited nodes.
* @param {(node: NodeID) => (NodeID[] | undefined)} navigation - Function to get
* neighbors/successors.
* @param {NodeID[]} acc - Accumulator for visited nodes.
*/
function doDfs(g, v, postorder, visited, navigation, acc) {
	if (!Object.prototype.hasOwnProperty.call(visited, v)) {
		visited[v] = true;
		if (!postorder) acc.push(v);
		forEach(navigation(v), function(w) {
			doDfs(g, w, postorder, visited, navigation, acc);
		});
		if (postorder) acc.push(v);
	}
}
//#endregion
//#region node_modules/dagre-d3-es/src/graphlib/alg/postorder.js
/**
* This function performs a [postorder traversal][] of the graph `g` starting
* at the nodes `vs`. For each node visited, `v`,  the function `callback(v)`
* is called.
*
* [postorder traversal]: https://en.wikipedia.org/wiki/Tree_traversal#Depth-first
*
* @example
*
* ![](https://github.com/dagrejs/graphlib/wiki/images/preorder.png)
*
* ```js
* graphlib.alg.postorder(g, "A");
* // => One of:
* // [ "B", "D", "E", C", "A" ]
* // [ "B", "E", "D", C", "A" ]
* // [ "D", "E", "C", B", "A" ]
* // [ "E", "D", "C", B", "A" ]
* ```
*
* @param {Parameters<typeof dfs>[0]} g - The graph to traverse.
* @param {Parameters<typeof dfs>[1]} vs - Nodes to start the traversal from.
* @returns {ReturnType<typeof dfs>} The nodes in the order they were visited.
*/
function postorder$1(g, vs) {
	return dfs$1(g, vs, "post");
}
//#endregion
//#region node_modules/dagre-d3-es/src/graphlib/alg/preorder.js
/**
* This function performs a [preorder traversal][] of the graph `g` starting
* at the nodes `vs`. For each node visited, `v`,  the function `callback(v)`
* is called.
*
* [preorder traversal]: https://en.wikipedia.org/wiki/Tree_traversal#Depth-first
*
* @example
*
* ![](https://github.com/dagrejs/graphlib/wiki/images/preorder.png)
* <!-- SOURCE:
* http://dagrejs.github.io/project/dagre-d3/latest/demo/interactive-demo.html?graph=digraph%20%7B%0Anode%20%5Bshape%3Dcircle%2C%20style%3D%22fill%3Awhite%3Bstroke%3A%23333%3Bstroke-width%3A1.5px%22%5D%0Aedge%20%5Blabeloffset%3D2%20labelpos%3Dr%5D%0Arankdir%3Dlr%0A%20%20A%20-%3E%20B%0A%20%20A%20-%3E%20C%0A%20%20C%20-%3E%20D%0A%20%20C%20-%3E%20E%0A%7D
* -->
*
* ```js
* graphlib.alg.preorder(g, "A");
* // => One of:
* // [ "A", "B", "C", "D", "E" ]
* // [ "A", "B", "C", "E", "D" ]
* // [ "A", "C", "D", "E", "B" ]
* // [ "A", "C", "E", "D", "B" ]
* ```
*
* @param {Parameters<typeof dfs>[0]} g - The graph to traverse.
* @param {Parameters<typeof dfs>[1]} vs - Nodes to start the traversal from.
* @returns {ReturnType<typeof dfs>} The nodes in the order they were visited.
*/
function preorder(g, vs) {
	return dfs$1(g, vs, "pre");
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/rank/network-simplex.js
networkSimplex.initLowLimValues = initLowLimValues;
networkSimplex.initCutValues = initCutValues;
networkSimplex.calcCutValue = calcCutValue;
networkSimplex.leaveEdge = leaveEdge;
networkSimplex.enterEdge = enterEdge;
networkSimplex.exchangeEdges = exchangeEdges;
function networkSimplex(g) {
	g = simplify(g);
	longestPath(g);
	var t = feasibleTree(g);
	initLowLimValues(t);
	initCutValues(t, g);
	var e, f;
	while (e = leaveEdge(t)) {
		f = enterEdge(t, g, e);
		exchangeEdges(t, g, e, f);
	}
}
function initCutValues(t, g) {
	var vs = postorder$1(t, t.nodes());
	vs = vs.slice(0, vs.length - 1);
	forEach(vs, function(v) {
		assignCutValue(t, g, v);
	});
}
function assignCutValue(t, g, child) {
	var parent = t.node(child).parent;
	t.edge(child, parent).cutvalue = calcCutValue(t, g, child);
}
function calcCutValue(t, g, child) {
	var parent = t.node(child).parent;
	var childIsTail = true;
	var graphEdge = g.edge(child, parent);
	var cutValue = 0;
	if (!graphEdge) {
		childIsTail = false;
		graphEdge = g.edge(parent, child);
	}
	cutValue = graphEdge.weight;
	forEach(g.nodeEdges(child), function(e) {
		var isOutEdge = e.v === child, other = isOutEdge ? e.w : e.v;
		if (other !== parent) {
			var pointsToHead = isOutEdge === childIsTail, otherWeight = g.edge(e).weight;
			cutValue += pointsToHead ? otherWeight : -otherWeight;
			if (isTreeEdge(t, child, other)) {
				var otherCutValue = t.edge(child, other).cutvalue;
				cutValue += pointsToHead ? -otherCutValue : otherCutValue;
			}
		}
	});
	return cutValue;
}
function initLowLimValues(tree, root) {
	if (arguments.length < 2) root = tree.nodes()[0];
	dfsAssignLowLim(tree, {}, 1, root);
}
function dfsAssignLowLim(tree, visited, nextLim, v, parent) {
	var low = nextLim;
	var label = tree.node(v);
	visited[v] = true;
	forEach(tree.neighbors(v), function(w) {
		if (!Object.prototype.hasOwnProperty.call(visited, w)) nextLim = dfsAssignLowLim(tree, visited, nextLim, w, v);
	});
	label.low = low;
	label.lim = nextLim++;
	if (parent) label.parent = parent;
	else delete label.parent;
	return nextLim;
}
function leaveEdge(tree) {
	return find(tree.edges(), function(e) {
		return tree.edge(e).cutvalue < 0;
	});
}
function enterEdge(t, g, edge) {
	var v = edge.v;
	var w = edge.w;
	if (!g.hasEdge(v, w)) {
		v = edge.w;
		w = edge.v;
	}
	var vLabel = t.node(v);
	var wLabel = t.node(w);
	var tailLabel = vLabel;
	var flip = false;
	if (vLabel.lim > wLabel.lim) {
		tailLabel = wLabel;
		flip = true;
	}
	return minBy(filter(g.edges(), function(edge) {
		return flip === isDescendant(t, t.node(edge.v), tailLabel) && flip !== isDescendant(t, t.node(edge.w), tailLabel);
	}), function(edge) {
		return slack(g, edge);
	});
}
function exchangeEdges(t, g, e, f) {
	var v = e.v;
	var w = e.w;
	t.removeEdge(v, w);
	t.setEdge(f.v, f.w, {});
	initLowLimValues(t);
	initCutValues(t, g);
	updateRanks(t, g);
}
function updateRanks(t, g) {
	var vs = preorder(t, find(t.nodes(), function(v) {
		return !g.node(v).parent;
	}));
	vs = vs.slice(1);
	forEach(vs, function(v) {
		var parent = t.node(v).parent, edge = g.edge(v, parent), flipped = false;
		if (!edge) {
			edge = g.edge(parent, v);
			flipped = true;
		}
		g.node(v).rank = g.node(parent).rank + (flipped ? edge.minlen : -edge.minlen);
	});
}
function isTreeEdge(tree, u, v) {
	return tree.hasEdge(u, v);
}
function isDescendant(tree, vLabel, rootLabel) {
	return rootLabel.low <= vLabel.lim && vLabel.lim <= rootLabel.lim;
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/rank/index.js
function rank(g) {
	switch (g.graph().ranker) {
		case "network-simplex":
			networkSimplexRanker(g);
			break;
		case "tight-tree":
			tightTreeRanker(g);
			break;
		case "longest-path":
			longestPathRanker(g);
			break;
		default: networkSimplexRanker(g);
	}
}
var longestPathRanker = longestPath;
function tightTreeRanker(g) {
	longestPath(g);
	feasibleTree(g);
}
function networkSimplexRanker(g) {
	networkSimplex(g);
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/nesting-graph.js
function run(g) {
	var root = addDummyNode(g, "root", {}, "_root");
	var depths = treeDepths(g);
	var height = max(values(depths)) - 1;
	var nodeSep = 2 * height + 1;
	g.graph().nestingRoot = root;
	forEach(g.edges(), function(e) {
		g.edge(e).minlen *= nodeSep;
	});
	var weight = sumWeights(g) + 1;
	forEach(g.children(), function(child) {
		dfs(g, root, nodeSep, weight, height, depths, child);
	});
	g.graph().nodeRankFactor = nodeSep;
}
function dfs(g, root, nodeSep, weight, height, depths, v) {
	var children = g.children(v);
	if (!children.length) {
		if (v !== root) g.setEdge(root, v, {
			weight: 0,
			minlen: nodeSep
		});
		return;
	}
	var top = addBorderNode$1(g, "_bt");
	var bottom = addBorderNode$1(g, "_bb");
	var label = g.node(v);
	g.setParent(top, v);
	label.borderTop = top;
	g.setParent(bottom, v);
	label.borderBottom = bottom;
	forEach(children, function(child) {
		dfs(g, root, nodeSep, weight, height, depths, child);
		var childNode = g.node(child);
		var childTop = childNode.borderTop ? childNode.borderTop : child;
		var childBottom = childNode.borderBottom ? childNode.borderBottom : child;
		var thisWeight = childNode.borderTop ? weight : 2 * weight;
		var minlen = childTop !== childBottom ? 1 : height - depths[v] + 1;
		g.setEdge(top, childTop, {
			weight: thisWeight,
			minlen,
			nestingEdge: true
		});
		g.setEdge(childBottom, bottom, {
			weight: thisWeight,
			minlen,
			nestingEdge: true
		});
	});
	if (!g.parent(v)) g.setEdge(root, top, {
		weight: 0,
		minlen: height + depths[v]
	});
}
function treeDepths(g) {
	var depths = {};
	function dfs(v, depth) {
		var children = g.children(v);
		if (children && children.length) forEach(children, function(child) {
			dfs(child, depth + 1);
		});
		depths[v] = depth;
	}
	forEach(g.children(), function(v) {
		dfs(v, 1);
	});
	return depths;
}
function sumWeights(g) {
	return reduce(g.edges(), function(acc, e) {
		return acc + g.edge(e).weight;
	}, 0);
}
function cleanup(g) {
	var graphLabel = g.graph();
	g.removeNode(graphLabel.nestingRoot);
	delete graphLabel.nestingRoot;
	forEach(g.edges(), function(e) {
		if (g.edge(e).nestingEdge) g.removeEdge(e);
	});
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/order/add-subgraph-constraints.js
function addSubgraphConstraints(g, cg, vs) {
	var prev = {}, rootPrev;
	forEach(vs, function(v) {
		var child = g.parent(v), parent, prevChild;
		while (child) {
			parent = g.parent(child);
			if (parent) {
				prevChild = prev[parent];
				prev[parent] = child;
			} else {
				prevChild = rootPrev;
				rootPrev = child;
			}
			if (prevChild && prevChild !== child) {
				cg.setEdge(prevChild, child);
				return;
			}
			child = parent;
		}
	});
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/order/build-layer-graph.js
function buildLayerGraph(g, rank, relationship) {
	var root = createRootNode(g), result = new Graph({ compound: true }).setGraph({ root }).setDefaultNodeLabel(function(v) {
		return g.node(v);
	});
	forEach(g.nodes(), function(v) {
		var node = g.node(v), parent = g.parent(v);
		if (node.rank === rank || node.minRank <= rank && rank <= node.maxRank) {
			result.setNode(v);
			result.setParent(v, parent || root);
			forEach(g[relationship](v), function(e) {
				var u = e.v === v ? e.w : e.v, edge = result.edge(u, v), weight = !isUndefined(edge) ? edge.weight : 0;
				result.setEdge(u, v, { weight: g.edge(e).weight + weight });
			});
			if (Object.prototype.hasOwnProperty.call(node, "minRank")) result.setNode(v, {
				borderLeft: node.borderLeft[rank],
				borderRight: node.borderRight[rank]
			});
		}
	});
	return result;
}
function createRootNode(g) {
	var v;
	while (g.hasNode(v = uniqueId("_root")));
	return v;
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/order/cross-count.js
function crossCount(g, layering) {
	var cc = 0;
	for (var i = 1; i < layering.length; ++i) cc += twoLayerCrossCount(g, layering[i - 1], layering[i]);
	return cc;
}
function twoLayerCrossCount(g, northLayer, southLayer) {
	var southPos = zipObject(southLayer, map(southLayer, function(v, i) {
		return i;
	}));
	var southEntries = flatten(map(northLayer, function(v) {
		return sortBy(map(g.outEdges(v), function(e) {
			return {
				pos: southPos[e.w],
				weight: g.edge(e).weight
			};
		}), "pos");
	}));
	var firstIndex = 1;
	while (firstIndex < southLayer.length) firstIndex <<= 1;
	var treeSize = 2 * firstIndex - 1;
	firstIndex -= 1;
	var tree = map(new Array(treeSize), function() {
		return 0;
	});
	var cc = 0;
	forEach(southEntries.forEach(function(entry) {
		var index = entry.pos + firstIndex;
		tree[index] += entry.weight;
		var weightSum = 0;
		while (index > 0) {
			if (index % 2) weightSum += tree[index + 1];
			index = index - 1 >> 1;
			tree[index] += entry.weight;
		}
		cc += entry.weight * weightSum;
	}));
	return cc;
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/order/init-order.js
function initOrder(g) {
	var visited = {};
	var simpleNodes = filter(g.nodes(), function(v) {
		return !g.children(v).length;
	});
	var layers = map(range(max(map(simpleNodes, function(v) {
		return g.node(v).rank;
	})) + 1), function() {
		return [];
	});
	function dfs(v) {
		if (has(visited, v)) return;
		visited[v] = true;
		layers[g.node(v).rank].push(v);
		forEach(g.successors(v), dfs);
	}
	forEach(sortBy(simpleNodes, function(v) {
		return g.node(v).rank;
	}), dfs);
	return layers;
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/order/barycenter.js
function barycenter(g, movable) {
	return map(movable, function(v) {
		var inV = g.inEdges(v);
		if (!inV.length) return { v };
		else {
			var result = reduce(inV, function(acc, e) {
				var edge = g.edge(e), nodeU = g.node(e.v);
				return {
					sum: acc.sum + edge.weight * nodeU.order,
					weight: acc.weight + edge.weight
				};
			}, {
				sum: 0,
				weight: 0
			});
			return {
				v,
				barycenter: result.sum / result.weight,
				weight: result.weight
			};
		}
	});
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/order/resolve-conflicts.js
function resolveConflicts(entries, cg) {
	var mappedEntries = {};
	forEach(entries, function(entry, i) {
		var tmp = mappedEntries[entry.v] = {
			indegree: 0,
			in: [],
			out: [],
			vs: [entry.v],
			i
		};
		if (!isUndefined(entry.barycenter)) {
			tmp.barycenter = entry.barycenter;
			tmp.weight = entry.weight;
		}
	});
	forEach(cg.edges(), function(e) {
		var entryV = mappedEntries[e.v];
		var entryW = mappedEntries[e.w];
		if (!isUndefined(entryV) && !isUndefined(entryW)) {
			entryW.indegree++;
			entryV.out.push(mappedEntries[e.w]);
		}
	});
	return doResolveConflicts(filter(mappedEntries, function(entry) {
		return !entry.indegree;
	}));
}
function doResolveConflicts(sourceSet) {
	var entries = [];
	function handleIn(vEntry) {
		return function(uEntry) {
			if (uEntry.merged) return;
			if (isUndefined(uEntry.barycenter) || isUndefined(vEntry.barycenter) || uEntry.barycenter >= vEntry.barycenter) mergeEntries(vEntry, uEntry);
		};
	}
	function handleOut(vEntry) {
		return function(wEntry) {
			wEntry["in"].push(vEntry);
			if (--wEntry.indegree === 0) sourceSet.push(wEntry);
		};
	}
	while (sourceSet.length) {
		var entry = sourceSet.pop();
		entries.push(entry);
		forEach(entry["in"].reverse(), handleIn(entry));
		forEach(entry.out, handleOut(entry));
	}
	return map(filter(entries, function(entry) {
		return !entry.merged;
	}), function(entry) {
		return pick(entry, [
			"vs",
			"i",
			"barycenter",
			"weight"
		]);
	});
}
function mergeEntries(target, source) {
	var sum = 0;
	var weight = 0;
	if (target.weight) {
		sum += target.barycenter * target.weight;
		weight += target.weight;
	}
	if (source.weight) {
		sum += source.barycenter * source.weight;
		weight += source.weight;
	}
	target.vs = source.vs.concat(target.vs);
	target.barycenter = sum / weight;
	target.weight = weight;
	target.i = Math.min(source.i, target.i);
	source.merged = true;
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/order/sort.js
function sort(entries, biasRight) {
	var parts = partition(entries, function(entry) {
		return Object.prototype.hasOwnProperty.call(entry, "barycenter");
	});
	var sortable = parts.lhs, unsortable = sortBy(parts.rhs, function(entry) {
		return -entry.i;
	}), vs = [], sum = 0, weight = 0, vsIndex = 0;
	sortable.sort(compareWithBias(!!biasRight));
	vsIndex = consumeUnsortable(vs, unsortable, vsIndex);
	forEach(sortable, function(entry) {
		vsIndex += entry.vs.length;
		vs.push(entry.vs);
		sum += entry.barycenter * entry.weight;
		weight += entry.weight;
		vsIndex = consumeUnsortable(vs, unsortable, vsIndex);
	});
	var result = { vs: flatten(vs) };
	if (weight) {
		result.barycenter = sum / weight;
		result.weight = weight;
	}
	return result;
}
function consumeUnsortable(vs, unsortable, index) {
	var last$1;
	while (unsortable.length && (last$1 = last(unsortable)).i <= index) {
		unsortable.pop();
		vs.push(last$1.vs);
		index++;
	}
	return index;
}
function compareWithBias(bias) {
	return function(entryV, entryW) {
		if (entryV.barycenter < entryW.barycenter) return -1;
		else if (entryV.barycenter > entryW.barycenter) return 1;
		return !bias ? entryV.i - entryW.i : entryW.i - entryV.i;
	};
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/order/sort-subgraph.js
function sortSubgraph(g, v, cg, biasRight) {
	var movable = g.children(v);
	var node = g.node(v);
	var bl = node ? node.borderLeft : void 0;
	var br = node ? node.borderRight : void 0;
	var subgraphs = {};
	if (bl) movable = filter(movable, function(w) {
		return w !== bl && w !== br;
	});
	var barycenters = barycenter(g, movable);
	forEach(barycenters, function(entry) {
		if (g.children(entry.v).length) {
			var subgraphResult = sortSubgraph(g, entry.v, cg, biasRight);
			subgraphs[entry.v] = subgraphResult;
			if (Object.prototype.hasOwnProperty.call(subgraphResult, "barycenter")) mergeBarycenters(entry, subgraphResult);
		}
	});
	var entries = resolveConflicts(barycenters, cg);
	expandSubgraphs(entries, subgraphs);
	var result = sort(entries, biasRight);
	if (bl) {
		result.vs = flatten([
			bl,
			result.vs,
			br
		]);
		if (g.predecessors(bl).length) {
			var blPred = g.node(g.predecessors(bl)[0]), brPred = g.node(g.predecessors(br)[0]);
			if (!Object.prototype.hasOwnProperty.call(result, "barycenter")) {
				result.barycenter = 0;
				result.weight = 0;
			}
			result.barycenter = (result.barycenter * result.weight + blPred.order + brPred.order) / (result.weight + 2);
			result.weight += 2;
		}
	}
	return result;
}
function expandSubgraphs(entries, subgraphs) {
	forEach(entries, function(entry) {
		entry.vs = flatten(entry.vs.map(function(v) {
			if (subgraphs[v]) return subgraphs[v].vs;
			return v;
		}));
	});
}
function mergeBarycenters(target, other) {
	if (!isUndefined(target.barycenter)) {
		target.barycenter = (target.barycenter * target.weight + other.barycenter * other.weight) / (target.weight + other.weight);
		target.weight += other.weight;
	} else {
		target.barycenter = other.barycenter;
		target.weight = other.weight;
	}
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/order/index.js
function order(g) {
	var maxRank$1 = maxRank(g), downLayerGraphs = buildLayerGraphs(g, range(1, maxRank$1 + 1), "inEdges"), upLayerGraphs = buildLayerGraphs(g, range(maxRank$1 - 1, -1, -1), "outEdges");
	var layering = initOrder(g);
	assignOrder(g, layering);
	var bestCC = Number.POSITIVE_INFINITY, best;
	for (var i = 0, lastBest = 0; lastBest < 4; ++i, ++lastBest) {
		sweepLayerGraphs(i % 2 ? downLayerGraphs : upLayerGraphs, i % 4 >= 2);
		layering = buildLayerMatrix(g);
		var cc = crossCount(g, layering);
		if (cc < bestCC) {
			lastBest = 0;
			best = cloneDeep(layering);
			bestCC = cc;
		}
	}
	assignOrder(g, best);
}
function buildLayerGraphs(g, ranks, relationship) {
	return map(ranks, function(rank) {
		return buildLayerGraph(g, rank, relationship);
	});
}
function sweepLayerGraphs(layerGraphs, biasRight) {
	var cg = new Graph();
	forEach(layerGraphs, function(lg) {
		var root = lg.graph().root;
		var sorted = sortSubgraph(lg, root, cg, biasRight);
		forEach(sorted.vs, function(v, i) {
			lg.node(v).order = i;
		});
		addSubgraphConstraints(lg, cg, sorted.vs);
	});
}
function assignOrder(g, layering) {
	forEach(layering, function(layer) {
		forEach(layer, function(v, i) {
			g.node(v).order = i;
		});
	});
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/parent-dummy-chains.js
function parentDummyChains(g) {
	var postorderNums = postorder(g);
	forEach(g.graph().dummyChains, function(v) {
		var node = g.node(v);
		var edgeObj = node.edgeObj;
		var pathData = findPath(g, postorderNums, edgeObj.v, edgeObj.w);
		var path = pathData.path;
		var lca = pathData.lca;
		var pathIdx = 0;
		var pathV = path[pathIdx];
		var ascending = true;
		while (v !== edgeObj.w) {
			node = g.node(v);
			if (ascending) {
				while ((pathV = path[pathIdx]) !== lca && g.node(pathV).maxRank < node.rank) pathIdx++;
				if (pathV === lca) ascending = false;
			}
			if (!ascending) {
				while (pathIdx < path.length - 1 && g.node(pathV = path[pathIdx + 1]).minRank <= node.rank) pathIdx++;
				pathV = path[pathIdx];
			}
			g.setParent(v, pathV);
			v = g.successors(v)[0];
		}
	});
}
function findPath(g, postorderNums, v, w) {
	var vPath = [];
	var wPath = [];
	var low = Math.min(postorderNums[v].low, postorderNums[w].low);
	var lim = Math.max(postorderNums[v].lim, postorderNums[w].lim);
	var parent;
	var lca;
	parent = v;
	do {
		parent = g.parent(parent);
		vPath.push(parent);
	} while (parent && (postorderNums[parent].low > low || lim > postorderNums[parent].lim));
	lca = parent;
	parent = w;
	while ((parent = g.parent(parent)) !== lca) wPath.push(parent);
	return {
		path: vPath.concat(wPath.reverse()),
		lca
	};
}
function postorder(g) {
	var result = {};
	var lim = 0;
	function dfs(v) {
		var low = lim;
		forEach(g.children(v), dfs);
		result[v] = {
			low,
			lim: lim++
		};
	}
	forEach(g.children(), dfs);
	return result;
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/position/bk.js
function findType1Conflicts(g, layering) {
	/** @type {{[nodeId: string | number]: {[nodeId: string | number]: true}}} */
	var conflicts = {};
	function visitLayer(prevLayer, layer) {
		var k0 = 0, scanPos = 0, prevLayerLength = prevLayer.length, lastNode = last(layer);
		forEach(layer, function(v, i) {
			var w = findOtherInnerSegmentNode(g, v), k1 = w ? g.node(w).order : prevLayerLength;
			if (w || v === lastNode) {
				forEach(layer.slice(scanPos, i + 1), function(scanNode) {
					forEach(g.predecessors(scanNode), function(u) {
						var uLabel = g.node(u), uPos = uLabel.order;
						if ((uPos < k0 || k1 < uPos) && !(uLabel.dummy && g.node(scanNode).dummy)) addConflict(conflicts, u, scanNode);
					});
				});
				scanPos = i + 1;
				k0 = k1;
			}
		});
		return layer;
	}
	reduce(layering, visitLayer);
	return conflicts;
}
function findType2Conflicts(g, layering) {
	/** @type {{[nodeId: string | number]: {[nodeId: string | number]: true}}} */
	var conflicts = {};
	function scan(south, southPos, southEnd, prevNorthBorder, nextNorthBorder) {
		var v;
		forEach(range(southPos, southEnd), function(i) {
			v = south[i];
			if (g.node(v).dummy) forEach(g.predecessors(v), function(u) {
				var uNode = g.node(u);
				if (uNode.dummy && (uNode.order < prevNorthBorder || uNode.order > nextNorthBorder)) addConflict(conflicts, u, v);
			});
		});
	}
	function visitLayer(north, south) {
		var prevNorthPos = -1, nextNorthPos, southPos = 0;
		forEach(south, function(v, southLookahead) {
			if (g.node(v).dummy === "border") {
				var predecessors = g.predecessors(v);
				if (predecessors.length) {
					nextNorthPos = g.node(predecessors[0]).order;
					scan(south, southPos, southLookahead, prevNorthPos, nextNorthPos);
					southPos = southLookahead;
					prevNorthPos = nextNorthPos;
				}
			}
			scan(south, southPos, south.length, nextNorthPos, north.length);
		});
		return south;
	}
	reduce(layering, visitLayer);
	return conflicts;
}
function findOtherInnerSegmentNode(g, v) {
	if (g.node(v).dummy) return find(g.predecessors(v), function(u) {
		return g.node(u).dummy;
	});
}
/**
* Sets `conflicts[v][w] = true`, creating objects if needed.
*
* @param {{[nodeId: string | number]: {[nodeId: string | number]: true}}} conflicts - Object to set.
* @param {string | number} v - First Node ID
* @param {string | number} w - Second Node ID
*/
function addConflict(conflicts, v, w) {
	if (v > w) {
		var tmp = v;
		v = w;
		w = tmp;
	}
	if (!Object.prototype.hasOwnProperty.call(conflicts, v)) Object.defineProperty(conflicts, v, {
		enumerable: true,
		configurable: true,
		value: {},
		writable: true
	});
	var conflictsV = conflicts[v];
	Object.defineProperty(conflictsV, w, {
		enumerable: true,
		configurable: true,
		value: true,
		writable: true
	});
}
function hasConflict(conflicts, v, w) {
	if (v > w) {
		var tmp = v;
		v = w;
		w = tmp;
	}
	return !!conflicts[v] && Object.prototype.hasOwnProperty.call(conflicts[v], w);
}
function verticalAlignment(g, layering, conflicts, neighborFn) {
	var root = {}, align = {}, pos = {};
	forEach(layering, function(layer) {
		forEach(layer, function(v, order) {
			root[v] = v;
			align[v] = v;
			pos[v] = order;
		});
	});
	forEach(layering, function(layer) {
		var prevIdx = -1;
		forEach(layer, function(v) {
			var ws = neighborFn(v);
			if (ws.length) {
				ws = sortBy(ws, function(w) {
					return pos[w];
				});
				var mp = (ws.length - 1) / 2;
				for (var i = Math.floor(mp), il = Math.ceil(mp); i <= il; ++i) {
					var w = ws[i];
					if (align[v] === v && prevIdx < pos[w] && !hasConflict(conflicts, v, w)) {
						align[w] = v;
						align[v] = root[v] = root[w];
						prevIdx = pos[w];
					}
				}
			}
		});
	});
	return {
		root,
		align
	};
}
function horizontalCompaction(g, layering, root, align, reverseSep) {
	/** @type {Record<import('../../graphlib/graph.js').NodeID, number>} */
	var xs = {}, blockG = buildBlockGraph(g, layering, root, reverseSep), borderType = reverseSep ? "borderLeft" : "borderRight";
	function iterate(setXsFunc, nextNodesFunc) {
		var stack = blockG.nodes();
		var elem = stack.pop();
		var visited = {};
		while (elem) {
			if (visited[elem]) setXsFunc(elem);
			else {
				visited[elem] = true;
				stack.push(elem);
				stack = stack.concat(nextNodesFunc(elem));
			}
			elem = stack.pop();
		}
	}
	function pass1(elem) {
		xs[elem] = blockG.inEdges(elem).reduce(function(acc, e) {
			return Math.max(acc, xs[e.v] + blockG.edge(e));
		}, 0);
	}
	function pass2(elem) {
		var min = blockG.outEdges(elem).reduce(function(acc, e) {
			return Math.min(acc, xs[e.w] - blockG.edge(e));
		}, Number.POSITIVE_INFINITY);
		var node = g.node(elem);
		if (min !== Number.POSITIVE_INFINITY && node.borderType !== borderType) xs[elem] = Math.max(xs[elem], min);
	}
	iterate(pass1, blockG.predecessors.bind(blockG));
	iterate(pass2, blockG.successors.bind(blockG));
	forEach(align, function(v) {
		xs[v] = xs[root[v]];
	});
	return xs;
}
function buildBlockGraph(g, layering, root, reverseSep) {
	var blockGraph = new Graph(), graphLabel = g.graph(), sepFn = sep(graphLabel.nodesep, graphLabel.edgesep, reverseSep);
	forEach(layering, function(layer) {
		var u;
		forEach(layer, function(v) {
			var vRoot = root[v];
			blockGraph.setNode(vRoot);
			if (u) {
				var uRoot = root[u], prevMax = blockGraph.edge(uRoot, vRoot);
				blockGraph.setEdge(uRoot, vRoot, Math.max(sepFn(g, v, u), prevMax || 0));
			}
			u = v;
		});
	});
	return blockGraph;
}
function findSmallestWidthAlignment(g, xss) {
	return minBy(values(xss), function(xs) {
		var max = Number.NEGATIVE_INFINITY;
		var min = Number.POSITIVE_INFINITY;
		forIn(xs, function(x, v) {
			var halfWidth = width(g, v) / 2;
			max = Math.max(x + halfWidth, max);
			min = Math.min(x - halfWidth, min);
		});
		return max - min;
	});
}
function alignCoordinates(xss, alignTo) {
	var alignToVals = values(alignTo), alignToMin = min(alignToVals), alignToMax = max(alignToVals);
	forEach(["u", "d"], function(vert) {
		forEach(["l", "r"], function(horiz) {
			var alignment = vert + horiz, xs = xss[alignment], delta;
			if (xs === alignTo) return;
			var xsVals = values(xs);
			delta = horiz === "l" ? alignToMin - min(xsVals) : alignToMax - max(xsVals);
			if (delta) xss[alignment] = mapValues(xs, function(x) {
				return x + delta;
			});
		});
	});
}
function balance(xss, align) {
	return mapValues(xss.ul, function(ignore, v) {
		if (align) return xss[align.toLowerCase()][v];
		else {
			var xs = sortBy(map(xss, v));
			return (xs[1] + xs[2]) / 2;
		}
	});
}
function positionX(g) {
	var layering = buildLayerMatrix(g);
	var conflicts = merge(findType1Conflicts(g, layering), findType2Conflicts(g, layering));
	var xss = {};
	var adjustedLayering;
	forEach(["u", "d"], function(vert) {
		adjustedLayering = vert === "u" ? layering : values(layering).reverse();
		forEach(["l", "r"], function(horiz) {
			if (horiz === "r") adjustedLayering = map(adjustedLayering, function(inner) {
				return values(inner).reverse();
			});
			var neighborFn = (vert === "u" ? g.predecessors : g.successors).bind(g);
			var align = verticalAlignment(g, adjustedLayering, conflicts, neighborFn);
			var xs = horizontalCompaction(g, adjustedLayering, align.root, align.align, horiz === "r");
			if (horiz === "r") xs = mapValues(xs, function(x) {
				return -x;
			});
			xss[vert + horiz] = xs;
		});
	});
	alignCoordinates(xss, findSmallestWidthAlignment(g, xss));
	return balance(xss, g.graph().align);
}
function sep(nodeSep, edgeSep, reverseSep) {
	return function(g, v, w) {
		var vLabel = g.node(v);
		var wLabel = g.node(w);
		var sum = 0;
		var delta;
		sum += vLabel.width / 2;
		if (Object.prototype.hasOwnProperty.call(vLabel, "labelpos")) switch (vLabel.labelpos.toLowerCase()) {
			case "l":
				delta = -vLabel.width / 2;
				break;
			case "r":
				delta = vLabel.width / 2;
				break;
		}
		if (delta) sum += reverseSep ? delta : -delta;
		delta = 0;
		sum += (vLabel.dummy ? edgeSep : nodeSep) / 2;
		sum += (wLabel.dummy ? edgeSep : nodeSep) / 2;
		sum += wLabel.width / 2;
		if (Object.prototype.hasOwnProperty.call(wLabel, "labelpos")) switch (wLabel.labelpos.toLowerCase()) {
			case "l":
				delta = wLabel.width / 2;
				break;
			case "r":
				delta = -wLabel.width / 2;
				break;
		}
		if (delta) sum += reverseSep ? delta : -delta;
		delta = 0;
		return sum;
	};
}
function width(g, v) {
	return g.node(v).width;
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/position/index.js
function position(g) {
	g = asNonCompoundGraph(g);
	positionY(g);
	forOwn(positionX(g), function(x, v) {
		g.node(v).x = x;
	});
}
function positionY(g) {
	var layering = buildLayerMatrix(g);
	var rankSep = g.graph().ranksep;
	var prevY = 0;
	forEach(layering, function(layer) {
		var maxHeight = max(map(layer, function(v) {
			return g.node(v).height;
		}));
		forEach(layer, function(v) {
			g.node(v).y = prevY + maxHeight / 2;
		});
		prevY += maxHeight + rankSep;
	});
}
//#endregion
//#region node_modules/dagre-d3-es/src/dagre/layout.js
function layout(g, opts) {
	var time$1 = opts && opts.debugTiming ? time : notime;
	time$1("layout", () => {
		var layoutGraph = time$1("  buildLayoutGraph", () => buildLayoutGraph(g));
		time$1("  runLayout", () => runLayout(layoutGraph, time$1));
		time$1("  updateInputGraph", () => updateInputGraph(g, layoutGraph));
	});
}
function runLayout(g, time) {
	time("    makeSpaceForEdgeLabels", () => makeSpaceForEdgeLabels(g));
	time("    removeSelfEdges", () => removeSelfEdges(g));
	time("    acyclic", () => run$2(g));
	time("    nestingGraph.run", () => run(g));
	time("    rank", () => rank(asNonCompoundGraph(g)));
	time("    injectEdgeLabelProxies", () => injectEdgeLabelProxies(g));
	time("    removeEmptyRanks", () => removeEmptyRanks(g));
	time("    nestingGraph.cleanup", () => cleanup(g));
	time("    normalizeRanks", () => normalizeRanks(g));
	time("    assignRankMinMax", () => assignRankMinMax(g));
	time("    removeEdgeLabelProxies", () => removeEdgeLabelProxies(g));
	time("    normalize.run", () => run$1(g));
	time("    parentDummyChains", () => parentDummyChains(g));
	time("    addBorderSegments", () => addBorderSegments(g));
	time("    order", () => order(g));
	time("    insertSelfEdges", () => insertSelfEdges(g));
	time("    adjustCoordinateSystem", () => adjust(g));
	time("    position", () => position(g));
	time("    positionSelfEdges", () => positionSelfEdges(g));
	time("    removeBorderNodes", () => removeBorderNodes(g));
	time("    normalize.undo", () => undo(g));
	time("    fixupEdgeLabelCoords", () => fixupEdgeLabelCoords(g));
	time("    undoCoordinateSystem", () => undo$1(g));
	time("    translateGraph", () => translateGraph(g));
	time("    assignNodeIntersects", () => assignNodeIntersects(g));
	time("    reversePoints", () => reversePointsForReversedEdges(g));
	time("    acyclic.undo", () => undo$2(g));
}
function updateInputGraph(inputGraph, layoutGraph) {
	forEach(inputGraph.nodes(), function(v) {
		var inputLabel = inputGraph.node(v);
		var layoutLabel = layoutGraph.node(v);
		if (inputLabel) {
			inputLabel.x = layoutLabel.x;
			inputLabel.y = layoutLabel.y;
			if (layoutGraph.children(v).length) {
				inputLabel.width = layoutLabel.width;
				inputLabel.height = layoutLabel.height;
			}
		}
	});
	forEach(inputGraph.edges(), function(e) {
		var inputLabel = inputGraph.edge(e);
		var layoutLabel = layoutGraph.edge(e);
		inputLabel.points = layoutLabel.points;
		if (Object.prototype.hasOwnProperty.call(layoutLabel, "x")) {
			inputLabel.x = layoutLabel.x;
			inputLabel.y = layoutLabel.y;
		}
	});
	inputGraph.graph().width = layoutGraph.graph().width;
	inputGraph.graph().height = layoutGraph.graph().height;
}
var graphNumAttrs = [
	"nodesep",
	"edgesep",
	"ranksep",
	"marginx",
	"marginy"
];
var graphDefaults = {
	ranksep: 50,
	edgesep: 20,
	nodesep: 50,
	rankdir: "tb"
};
var graphAttrs = [
	"acyclicer",
	"ranker",
	"rankdir",
	"align"
];
var nodeNumAttrs = ["width", "height"];
var nodeDefaults = {
	width: 0,
	height: 0
};
var edgeNumAttrs = [
	"minlen",
	"weight",
	"width",
	"height",
	"labeloffset"
];
var edgeDefaults = {
	minlen: 1,
	weight: 1,
	width: 0,
	height: 0,
	labeloffset: 10,
	labelpos: "r"
};
var edgeAttrs = ["labelpos"];
function buildLayoutGraph(inputGraph) {
	var g = new Graph({
		multigraph: true,
		compound: true
	});
	var graph = canonicalize(inputGraph.graph());
	g.setGraph(merge({}, graphDefaults, selectNumberAttrs(graph, graphNumAttrs), pick(graph, graphAttrs)));
	forEach(inputGraph.nodes(), function(v) {
		var node = canonicalize(inputGraph.node(v));
		g.setNode(v, defaults(selectNumberAttrs(node, nodeNumAttrs), nodeDefaults));
		g.setParent(v, inputGraph.parent(v));
	});
	forEach(inputGraph.edges(), function(e) {
		var edge = canonicalize(inputGraph.edge(e));
		g.setEdge(e, merge({}, edgeDefaults, selectNumberAttrs(edge, edgeNumAttrs), pick(edge, edgeAttrs)));
	});
	return g;
}
function makeSpaceForEdgeLabels(g) {
	var graph = g.graph();
	graph.ranksep /= 2;
	forEach(g.edges(), function(e) {
		var edge = g.edge(e);
		edge.minlen *= 2;
		if (edge.labelpos.toLowerCase() !== "c") if (graph.rankdir === "TB" || graph.rankdir === "BT") edge.width += edge.labeloffset;
		else edge.height += edge.labeloffset;
	});
}
function injectEdgeLabelProxies(g) {
	forEach(g.edges(), function(e) {
		var edge = g.edge(e);
		if (edge.width && edge.height) {
			var v = g.node(e.v);
			addDummyNode(g, "edge-proxy", {
				rank: (g.node(e.w).rank - v.rank) / 2 + v.rank,
				e
			}, "_ep");
		}
	});
}
function assignRankMinMax(g) {
	var maxRank = 0;
	forEach(g.nodes(), function(v) {
		var node = g.node(v);
		if (node.borderTop) {
			node.minRank = g.node(node.borderTop).rank;
			node.maxRank = g.node(node.borderBottom).rank;
			maxRank = max(maxRank, node.maxRank);
		}
	});
	g.graph().maxRank = maxRank;
}
function removeEdgeLabelProxies(g) {
	forEach(g.nodes(), function(v) {
		var node = g.node(v);
		if (node.dummy === "edge-proxy") {
			g.edge(node.e).labelRank = node.rank;
			g.removeNode(v);
		}
	});
}
function translateGraph(g) {
	var minX = Number.POSITIVE_INFINITY;
	var maxX = 0;
	var minY = Number.POSITIVE_INFINITY;
	var maxY = 0;
	var graphLabel = g.graph();
	var marginX = graphLabel.marginx || 0;
	var marginY = graphLabel.marginy || 0;
	function getExtremes(attrs) {
		var x = attrs.x;
		var y = attrs.y;
		var w = attrs.width;
		var h = attrs.height;
		minX = Math.min(minX, x - w / 2);
		maxX = Math.max(maxX, x + w / 2);
		minY = Math.min(minY, y - h / 2);
		maxY = Math.max(maxY, y + h / 2);
	}
	forEach(g.nodes(), function(v) {
		getExtremes(g.node(v));
	});
	forEach(g.edges(), function(e) {
		var edge = g.edge(e);
		if (Object.prototype.hasOwnProperty.call(edge, "x")) getExtremes(edge);
	});
	minX -= marginX;
	minY -= marginY;
	forEach(g.nodes(), function(v) {
		var node = g.node(v);
		node.x -= minX;
		node.y -= minY;
	});
	forEach(g.edges(), function(e) {
		var edge = g.edge(e);
		forEach(edge.points, function(p) {
			p.x -= minX;
			p.y -= minY;
		});
		if (Object.prototype.hasOwnProperty.call(edge, "x")) edge.x -= minX;
		if (Object.prototype.hasOwnProperty.call(edge, "y")) edge.y -= minY;
	});
	graphLabel.width = maxX - minX + marginX;
	graphLabel.height = maxY - minY + marginY;
}
function assignNodeIntersects(g) {
	forEach(g.edges(), function(e) {
		var edge = g.edge(e);
		var nodeV = g.node(e.v);
		var nodeW = g.node(e.w);
		var p1, p2;
		if (!edge.points) {
			edge.points = [];
			p1 = nodeW;
			p2 = nodeV;
		} else {
			p1 = edge.points[0];
			p2 = edge.points[edge.points.length - 1];
		}
		edge.points.unshift(intersectRect(nodeV, p1));
		edge.points.push(intersectRect(nodeW, p2));
	});
}
function fixupEdgeLabelCoords(g) {
	forEach(g.edges(), function(e) {
		var edge = g.edge(e);
		if (Object.prototype.hasOwnProperty.call(edge, "x")) {
			if (edge.labelpos === "l" || edge.labelpos === "r") edge.width -= edge.labeloffset;
			switch (edge.labelpos) {
				case "l":
					edge.x -= edge.width / 2 + edge.labeloffset;
					break;
				case "r":
					edge.x += edge.width / 2 + edge.labeloffset;
					break;
			}
		}
	});
}
function reversePointsForReversedEdges(g) {
	forEach(g.edges(), function(e) {
		var edge = g.edge(e);
		if (edge.reversed) edge.points.reverse();
	});
}
function removeBorderNodes(g) {
	forEach(g.nodes(), function(v) {
		if (g.children(v).length) {
			var node = g.node(v);
			var t = g.node(node.borderTop);
			var b = g.node(node.borderBottom);
			var l = g.node(last(node.borderLeft));
			var r = g.node(last(node.borderRight));
			node.width = Math.abs(r.x - l.x);
			node.height = Math.abs(b.y - t.y);
			node.x = l.x + node.width / 2;
			node.y = t.y + node.height / 2;
		}
	});
	forEach(g.nodes(), function(v) {
		if (g.node(v).dummy === "border") g.removeNode(v);
	});
}
function removeSelfEdges(g) {
	forEach(g.edges(), function(e) {
		if (e.v === e.w) {
			var node = g.node(e.v);
			if (!node.selfEdges) node.selfEdges = [];
			node.selfEdges.push({
				e,
				label: g.edge(e)
			});
			g.removeEdge(e);
		}
	});
}
function insertSelfEdges(g) {
	forEach(buildLayerMatrix(g), function(layer) {
		var orderShift = 0;
		forEach(layer, function(v, i) {
			var node = g.node(v);
			node.order = i + orderShift;
			forEach(node.selfEdges, function(selfEdge) {
				addDummyNode(g, "selfedge", {
					width: selfEdge.label.width,
					height: selfEdge.label.height,
					rank: node.rank,
					order: i + ++orderShift,
					e: selfEdge.e,
					label: selfEdge.label
				}, "_se");
			});
			delete node.selfEdges;
		});
	});
}
function positionSelfEdges(g) {
	forEach(g.nodes(), function(v) {
		var node = g.node(v);
		if (node.dummy === "selfedge") {
			var selfNode = g.node(node.e.v);
			var x = selfNode.x + selfNode.width / 2;
			var y = selfNode.y;
			var dx = node.x - x;
			var dy = selfNode.height / 2;
			g.setEdge(node.e, node.label);
			g.removeNode(v);
			node.label.points = [
				{
					x: x + 2 * dx / 3,
					y: y - dy
				},
				{
					x: x + 5 * dx / 6,
					y: y - dy
				},
				{
					x: x + dx,
					y
				},
				{
					x: x + 5 * dx / 6,
					y: y + dy
				},
				{
					x: x + 2 * dx / 3,
					y: y + dy
				}
			];
			node.label.x = node.x;
			node.label.y = node.y;
		}
	});
}
function selectNumberAttrs(obj, attrs) {
	return mapValues(pick(obj, attrs), Number);
}
function canonicalize(attrs) {
	var newAttrs = {};
	forEach(attrs, function(v, k) {
		newAttrs[k.toLowerCase()] = v;
	});
	return newAttrs;
}
//#endregion
//#region node_modules/dagre-d3-es/src/graphlib/json.js
/**
* @template [GraphLabel=any] - Label of the graph.
* @template [NodeLabel=any] - Label of a node.
* @template [EdgeLabel=any] - Label of an edge.
*
* @typedef {object} GraphJSON
* @property {Required<GraphOptions>} options - The options used to create the graph.
* @property {Array<{ v: NodeID; value?: NodeLabel; parent?: NodeID }>} nodes - The nodes in the graph.
* @property {Array<EdgeObj & { value?: EdgeLabel }>} edges - The edges in the graph.
* @property {GraphLabel} [value] - The graph's value, if any.
*/
/**
* Creates a JSON representation of the graph that can be serialized to a
* string with
* [JSON.stringify](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/JSON/stringify).
* The graph can later be restored using {@link read}.
*
* @example
*
* ```js
* var g = new graphlib.Graph();
* g.setNode("a", { label: "node a" });
* g.setNode("b", { label: "node b" });
* g.setEdge("a", "b", { label: "edge a->b" });
* graphlib.json.write(g);
* // Returns the object:
* //
* // {
* //   "options": {
* //     "directed": true,
* //     "multigraph": false,
* //     "compound": false
* //   },
* //   "nodes": [
* //     { "v": "a", "value": { "label": "node a" } },
* //     { "v": "b", "value": { "label": "node b" } }
* //   ],
* //   "edges": [
* //     { "v": "a", "w": "b", "value": { "label": "edge a->b" } }
* //   ]
* // }
* ```
*
* @template [GraphLabel=any] - Label of the graph.
* @template [NodeLabel=any] - Label of a node.
* @template [EdgeLabel=any] - Label of an edge.
* @param {Graph<GraphLabel, NodeLabel, EdgeLabel>} g - The graph to serialize.
* @returns {GraphJSON<GraphLabel, NodeLabel, EdgeLabel>} The JSON representation of the graph.
*/
function write(g) {
	/** @type {GraphJSON<GraphLabel, NodeLabel, EdgeLabel>} */
	var json = {
		options: {
			directed: g.isDirected(),
			multigraph: g.isMultigraph(),
			compound: g.isCompound()
		},
		nodes: writeNodes(g),
		edges: writeEdges(g)
	};
	if (!isUndefined(g.graph())) json.value = clone(g.graph());
	return json;
}
/**
* @template NodeLabel - Label of a node.
*
* @param {Graph<unknown, NodeLabel, unknown>} g - The graph to serialize.
* @returns {Array<{ v: NodeID; value?: NodeLabel; parent?: NodeID }>} The nodes in the graph.
*/
function writeNodes(g) {
	return map(g.nodes(), function(v) {
		var nodeValue = g.node(v);
		var parent = g.parent(v);
		/** @type {{ v: NodeID; value?: NodeLabel; parent?: NodeID }} */
		var node = { v };
		if (!isUndefined(nodeValue)) node.value = nodeValue;
		if (!isUndefined(parent)) node.parent = parent;
		return node;
	});
}
/**
* @template EdgeLabel - Label of a node.
*
* @param {Graph<unknown, unknown, EdgeLabel>} g - The graph to serialize.
* @returns {Array<EdgeObj & { value?: EdgeLabel }>} The edges in the graph.
*/
function writeEdges(g) {
	return map(g.edges(), function(e) {
		var edgeValue = g.edge(e);
		/** @type {EdgeObj & { value?: EdgeLabel }} */
		var edge = {
			v: e.v,
			w: e.w
		};
		if (!isUndefined(e.name)) edge.name = e.name;
		if (!isUndefined(edgeValue)) edge.value = edgeValue;
		return edge;
	});
}
//#endregion
export { merge as a, clone as i, layout as n, Graph as r, write as t };
