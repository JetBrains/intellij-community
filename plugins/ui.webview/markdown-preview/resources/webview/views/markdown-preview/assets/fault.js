import { r as __toESM, t as __commonJSMin } from "./rolldown-runtime.js";
var import_format = /* @__PURE__ */ __toESM((/* @__PURE__ */ __commonJSMin(((exports, module) => {
	(function() {
		var namespace;
		if (typeof module !== "undefined") namespace = module.exports = format;
		else namespace = function() {
			return this || (0, eval)("this");
		}();
		namespace.format = format;
		namespace.vsprintf = vsprintf;
		if (typeof console !== "undefined" && typeof console.log === "function") namespace.printf = printf;
		function printf() {
			console.log(format.apply(null, arguments));
		}
		function vsprintf(fmt, replacements) {
			return format.apply(null, [fmt].concat(replacements));
		}
		function format(fmt) {
			var argIndex = 1, args = [].slice.call(arguments), i = 0, n = fmt.length, result = "", c, escaped = false, arg, tmp, leadingZero = false, precision, nextArg = function() {
				return args[argIndex++];
			}, slurpNumber = function() {
				var digits = "";
				while (/\d/.test(fmt[i])) {
					digits += fmt[i++];
					c = fmt[i];
				}
				return digits.length > 0 ? parseInt(digits) : null;
			};
			for (; i < n; ++i) {
				c = fmt[i];
				if (escaped) {
					escaped = false;
					if (c == ".") {
						leadingZero = false;
						c = fmt[++i];
					} else if (c == "0" && fmt[i + 1] == ".") {
						leadingZero = true;
						i += 2;
						c = fmt[i];
					} else leadingZero = true;
					precision = slurpNumber();
					switch (c) {
						case "b":
							result += parseInt(nextArg(), 10).toString(2);
							break;
						case "c":
							arg = nextArg();
							if (typeof arg === "string" || arg instanceof String) result += arg;
							else result += String.fromCharCode(parseInt(arg, 10));
							break;
						case "d":
							result += parseInt(nextArg(), 10);
							break;
						case "f":
							tmp = String(parseFloat(nextArg()).toFixed(precision || 6));
							result += leadingZero ? tmp : tmp.replace(/^0/, "");
							break;
						case "j":
							result += JSON.stringify(nextArg());
							break;
						case "o":
							result += "0" + parseInt(nextArg(), 10).toString(8);
							break;
						case "s":
							result += nextArg();
							break;
						case "x":
							result += "0x" + parseInt(nextArg(), 10).toString(16);
							break;
						case "X":
							result += "0x" + parseInt(nextArg(), 10).toString(16).toUpperCase();
							break;
						default:
							result += c;
							break;
					}
				} else if (c === "%") escaped = true;
				else result += c;
			}
			return result;
		}
	})();
})))(), 1);
var fault = Object.assign(create(Error), {
	eval: create(EvalError),
	range: create(RangeError),
	reference: create(ReferenceError),
	syntax: create(SyntaxError),
	type: create(TypeError),
	uri: create(URIError)
});
/**
* Create a new `EConstructor`, with the formatted `format` as a first argument.
*
* @template {Error} Fault
* @template {new (reason: string) => Fault} Class
* @param {Class} Constructor
*/
function create(Constructor) {
	/** @type {string} */
	FormattedError.displayName = Constructor.displayName || Constructor.name;
	return FormattedError;
	/**
	* Create an error with a printf-like formatted message.
	*
	* @param {string|null} [format]
	*   Template string.
	* @param {...unknown} values
	*   Values to render in `format`.
	* @returns {Fault}
	*/
	function FormattedError(format, ...values) {
		return new Constructor(format ? (0, import_format.default)(format, ...values) : format);
	}
}
export { fault as t };
