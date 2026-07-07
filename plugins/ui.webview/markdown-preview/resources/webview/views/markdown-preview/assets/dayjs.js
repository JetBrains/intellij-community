import { t as __commonJSMin } from "./rolldown-runtime.js";
//#region node_modules/.bun/dayjs@1.11.20/node_modules/dayjs/dayjs.min.js
var require_dayjs_min = /* @__PURE__ */ __commonJSMin(((exports, module) => {
	(function(t, e) {
		"object" == typeof exports && "undefined" != typeof module ? module.exports = e() : "function" == typeof define && define.amd ? define(e) : (t = "undefined" != typeof globalThis ? globalThis : t || self).dayjs = e();
	})(exports, (function() {
		"use strict";
		var t = 1e3, e = 6e4, n = 36e5, r = "millisecond", i = "second", s = "minute", u = "hour", a = "day", o = "week", c = "month", f = "quarter", h = "year", d = "date", l = "Invalid Date", $ = /^(\d{4})[-/]?(\d{1,2})?[-/]?(\d{0,2})[Tt\s]*(\d{1,2})?:?(\d{1,2})?:?(\d{1,2})?[.:]?(\d+)?$/, y = /\[([^\]]+)]|Y{1,4}|M{1,4}|D{1,2}|d{1,4}|H{1,2}|h{1,2}|a|A|m{1,2}|s{1,2}|Z{1,2}|SSS/g, M = {
			name: "en",
			weekdays: "Sunday_Monday_Tuesday_Wednesday_Thursday_Friday_Saturday".split("_"),
			months: "January_February_March_April_May_June_July_August_September_October_November_December".split("_"),
			ordinal: function(t) {
				var e = [
					"th",
					"st",
					"nd",
					"rd"
				], n = t % 100;
				return "[" + t + (e[(n - 20) % 10] || e[n] || e[0]) + "]";
			}
		}, m = function(t, e, n) {
			var r = String(t);
			return !r || r.length >= e ? t : "" + Array(e + 1 - r.length).join(n) + t;
		}, v = {
			s: m,
			z: function(t) {
				var e = -t.utcOffset(), n = Math.abs(e), r = Math.floor(n / 60), i = n % 60;
				return (e <= 0 ? "+" : "-") + m(r, 2, "0") + ":" + m(i, 2, "0");
			},
			m: function t(e, n) {
				if (e.date() < n.date()) return -t(n, e);
				var r = 12 * (n.year() - e.year()) + (n.month() - e.month()), i = e.clone().add(r, c), s = n - i < 0, u = e.clone().add(r + (s ? -1 : 1), c);
				return +(-(r + (n - i) / (s ? i - u : u - i)) || 0);
			},
			a: function(t) {
				return t < 0 ? Math.ceil(t) || 0 : Math.floor(t);
			},
			p: function(t) {
				return {
					M: c,
					y: h,
					w: o,
					d: a,
					D: d,
					h: u,
					m: s,
					s: i,
					ms: r,
					Q: f
				}[t] || String(t || "").toLowerCase().replace(/s$/, "");
			},
			u: function(t) {
				return void 0 === t;
			}
		}, g = "en", D = {};
		D[g] = M;
		var p = "$isDayjsObject", S = function(t) {
			return t instanceof _ || !(!t || !t[p]);
		}, w = function t(e, n, r) {
			var i;
			if (!e) return g;
			if ("string" == typeof e) {
				var s = e.toLowerCase();
				D[s] && (i = s), n && (D[s] = n, i = s);
				var u = e.split("-");
				if (!i && u.length > 1) return t(u[0]);
			} else {
				var a = e.name;
				D[a] = e, i = a;
			}
			return !r && i && (g = i), i || !r && g;
		}, O = function(t, e) {
			if (S(t)) return t.clone();
			var n = "object" == typeof e ? e : {};
			return n.date = t, n.args = arguments, new _(n);
		}, b = v;
		b.l = w, b.i = S, b.w = function(t, e) {
			return O(t, {
				locale: e.$L,
				utc: e.$u,
				x: e.$x,
				$offset: e.$offset
			});
		};
		var _ = function() {
			function M(t) {
				this.$L = w(t.locale, null, !0), this.parse(t), this.$x = this.$x || t.x || {}, this[p] = !0;
			}
			var m = M.prototype;
			return m.parse = function(t) {
				this.$d = function(t) {
					var e = t.date, n = t.utc;
					if (null === e) return /* @__PURE__ */ new Date(NaN);
					if (b.u(e)) return /* @__PURE__ */ new Date();
					if (e instanceof Date) return new Date(e);
					if ("string" == typeof e && !/Z$/i.test(e)) {
						var r = e.match($);
						if (r) {
							var i = r[2] - 1 || 0, s = (r[7] || "0").substring(0, 3);
							return n ? new Date(Date.UTC(r[1], i, r[3] || 1, r[4] || 0, r[5] || 0, r[6] || 0, s)) : new Date(r[1], i, r[3] || 1, r[4] || 0, r[5] || 0, r[6] || 0, s);
						}
					}
					return new Date(e);
				}(t), this.init();
			}, m.init = function() {
				var t = this.$d;
				this.$y = t.getFullYear(), this.$M = t.getMonth(), this.$D = t.getDate(), this.$W = t.getDay(), this.$H = t.getHours(), this.$m = t.getMinutes(), this.$s = t.getSeconds(), this.$ms = t.getMilliseconds();
			}, m.$utils = function() {
				return b;
			}, m.isValid = function() {
				return !(this.$d.toString() === l);
			}, m.isSame = function(t, e) {
				var n = O(t);
				return this.startOf(e) <= n && n <= this.endOf(e);
			}, m.isAfter = function(t, e) {
				return O(t) < this.startOf(e);
			}, m.isBefore = function(t, e) {
				return this.endOf(e) < O(t);
			}, m.$g = function(t, e, n) {
				return b.u(t) ? this[e] : this.set(n, t);
			}, m.unix = function() {
				return Math.floor(this.valueOf() / 1e3);
			}, m.valueOf = function() {
				return this.$d.getTime();
			}, m.startOf = function(t, e) {
				var n = this, r = !!b.u(e) || e, f = b.p(t), l = function(t, e) {
					var i = b.w(n.$u ? Date.UTC(n.$y, e, t) : new Date(n.$y, e, t), n);
					return r ? i : i.endOf(a);
				}, $ = function(t, e) {
					return b.w(n.toDate()[t].apply(n.toDate("s"), (r ? [
						0,
						0,
						0,
						0
					] : [
						23,
						59,
						59,
						999
					]).slice(e)), n);
				}, y = this.$W, M = this.$M, m = this.$D, v = "set" + (this.$u ? "UTC" : "");
				switch (f) {
					case h: return r ? l(1, 0) : l(31, 11);
					case c: return r ? l(1, M) : l(0, M + 1);
					case o:
						var g = this.$locale().weekStart || 0, D = (y < g ? y + 7 : y) - g;
						return l(r ? m - D : m + (6 - D), M);
					case a:
					case d: return $(v + "Hours", 0);
					case u: return $(v + "Minutes", 1);
					case s: return $(v + "Seconds", 2);
					case i: return $(v + "Milliseconds", 3);
					default: return this.clone();
				}
			}, m.endOf = function(t) {
				return this.startOf(t, !1);
			}, m.$set = function(t, e) {
				var n, o = b.p(t), f = "set" + (this.$u ? "UTC" : ""), l = (n = {}, n[a] = f + "Date", n[d] = f + "Date", n[c] = f + "Month", n[h] = f + "FullYear", n[u] = f + "Hours", n[s] = f + "Minutes", n[i] = f + "Seconds", n[r] = f + "Milliseconds", n)[o], $ = o === a ? this.$D + (e - this.$W) : e;
				if (o === c || o === h) {
					var y = this.clone().set(d, 1);
					y.$d[l]($), y.init(), this.$d = y.set(d, Math.min(this.$D, y.daysInMonth())).$d;
				} else l && this.$d[l]($);
				return this.init(), this;
			}, m.set = function(t, e) {
				return this.clone().$set(t, e);
			}, m.get = function(t) {
				return this[b.p(t)]();
			}, m.add = function(r, f) {
				var d, l = this;
				r = Number(r);
				var $ = b.p(f), y = function(t) {
					var e = O(l);
					return b.w(e.date(e.date() + Math.round(t * r)), l);
				};
				if ($ === c) return this.set(c, this.$M + r);
				if ($ === h) return this.set(h, this.$y + r);
				if ($ === a) return y(1);
				if ($ === o) return y(7);
				var M = (d = {}, d[s] = e, d[u] = n, d[i] = t, d)[$] || 1, m = this.$d.getTime() + r * M;
				return b.w(m, this);
			}, m.subtract = function(t, e) {
				return this.add(-1 * t, e);
			}, m.format = function(t) {
				var e = this, n = this.$locale();
				if (!this.isValid()) return n.invalidDate || l;
				var r = t || "YYYY-MM-DDTHH:mm:ssZ", i = b.z(this), s = this.$H, u = this.$m, a = this.$M, o = n.weekdays, c = n.months, f = n.meridiem, h = function(t, n, i, s) {
					return t && (t[n] || t(e, r)) || i[n].slice(0, s);
				}, d = function(t) {
					return b.s(s % 12 || 12, t, "0");
				}, $ = f || function(t, e, n) {
					var r = t < 12 ? "AM" : "PM";
					return n ? r.toLowerCase() : r;
				};
				return r.replace(y, (function(t, r) {
					return r || function(t) {
						switch (t) {
							case "YY": return String(e.$y).slice(-2);
							case "YYYY": return b.s(e.$y, 4, "0");
							case "M": return a + 1;
							case "MM": return b.s(a + 1, 2, "0");
							case "MMM": return h(n.monthsShort, a, c, 3);
							case "MMMM": return h(c, a);
							case "D": return e.$D;
							case "DD": return b.s(e.$D, 2, "0");
							case "d": return String(e.$W);
							case "dd": return h(n.weekdaysMin, e.$W, o, 2);
							case "ddd": return h(n.weekdaysShort, e.$W, o, 3);
							case "dddd": return o[e.$W];
							case "H": return String(s);
							case "HH": return b.s(s, 2, "0");
							case "h": return d(1);
							case "hh": return d(2);
							case "a": return $(s, u, !0);
							case "A": return $(s, u, !1);
							case "m": return String(u);
							case "mm": return b.s(u, 2, "0");
							case "s": return String(e.$s);
							case "ss": return b.s(e.$s, 2, "0");
							case "SSS": return b.s(e.$ms, 3, "0");
							case "Z": return i;
						}
						return null;
					}(t) || i.replace(":", "");
				}));
			}, m.utcOffset = function() {
				return 15 * -Math.round(this.$d.getTimezoneOffset() / 15);
			}, m.diff = function(r, d, l) {
				var $, y = this, M = b.p(d), m = O(r), v = (m.utcOffset() - this.utcOffset()) * e, g = this - m, D = function() {
					return b.m(y, m);
				};
				switch (M) {
					case h:
						$ = D() / 12;
						break;
					case c:
						$ = D();
						break;
					case f:
						$ = D() / 3;
						break;
					case o:
						$ = (g - v) / 6048e5;
						break;
					case a:
						$ = (g - v) / 864e5;
						break;
					case u:
						$ = g / n;
						break;
					case s:
						$ = g / e;
						break;
					case i:
						$ = g / t;
						break;
					default: $ = g;
				}
				return l ? $ : b.a($);
			}, m.daysInMonth = function() {
				return this.endOf(c).$D;
			}, m.$locale = function() {
				return D[this.$L];
			}, m.locale = function(t, e) {
				if (!t) return this.$L;
				var n = this.clone(), r = w(t, e, !0);
				return r && (n.$L = r), n;
			}, m.clone = function() {
				return b.w(this.$d, this);
			}, m.toDate = function() {
				return new Date(this.valueOf());
			}, m.toJSON = function() {
				return this.isValid() ? this.toISOString() : null;
			}, m.toISOString = function() {
				return this.$d.toISOString();
			}, m.toString = function() {
				return this.$d.toUTCString();
			}, M;
		}(), k = _.prototype;
		return O.prototype = k, [
			["$ms", r],
			["$s", i],
			["$m", s],
			["$H", u],
			["$W", a],
			["$M", c],
			["$y", h],
			["$D", d]
		].forEach((function(t) {
			k[t[1]] = function(e) {
				return this.$g(e, t[0], t[1]);
			};
		})), O.extend = function(t, e) {
			return t.$i || (t(e, _, O), t.$i = !0), O;
		}, O.locale = w, O.isDayjs = S, O.unix = function(t) {
			return O(1e3 * t);
		}, O.en = D[g], O.Ls = D, O.p = {}, O;
	}));
}));
//#endregion
//#region node_modules/.bun/dayjs@1.11.20/node_modules/dayjs/plugin/isoWeek.js
var require_isoWeek = /* @__PURE__ */ __commonJSMin(((exports, module) => {
	(function(e, t) {
		"object" == typeof exports && "undefined" != typeof module ? module.exports = t() : "function" == typeof define && define.amd ? define(t) : (e = "undefined" != typeof globalThis ? globalThis : e || self).dayjs_plugin_isoWeek = t();
	})(exports, (function() {
		"use strict";
		var e = "day";
		return function(t, i, s) {
			var a = function(t) {
				return t.add(4 - t.isoWeekday(), e);
			}, d = i.prototype;
			d.isoWeekYear = function() {
				return a(this).year();
			}, d.isoWeek = function(t) {
				if (!this.$utils().u(t)) return this.add(7 * (t - this.isoWeek()), e);
				var i, d, n, o, r = a(this), u = (i = this.isoWeekYear(), d = this.$u, n = (d ? s.utc : s)().year(i).startOf("year"), o = 4 - n.isoWeekday(), n.isoWeekday() > 4 && (o += 7), n.add(o, e));
				return r.diff(u, "week") + 1;
			}, d.isoWeekday = function(e) {
				return this.$utils().u(e) ? this.day() || 7 : this.day(this.day() % 7 ? e : e - 7);
			};
			var n = d.startOf;
			d.startOf = function(e, t) {
				var i = this.$utils(), s = !!i.u(t) || t;
				return "isoweek" === i.p(e) ? s ? this.date(this.date() - (this.isoWeekday() - 1)).startOf("day") : this.date(this.date() - 1 - (this.isoWeekday() - 1) + 7).endOf("day") : n.bind(this)(e, t);
			};
		};
	}));
}));
//#endregion
//#region node_modules/.bun/dayjs@1.11.20/node_modules/dayjs/plugin/customParseFormat.js
var require_customParseFormat = /* @__PURE__ */ __commonJSMin(((exports, module) => {
	(function(e, t) {
		"object" == typeof exports && "undefined" != typeof module ? module.exports = t() : "function" == typeof define && define.amd ? define(t) : (e = "undefined" != typeof globalThis ? globalThis : e || self).dayjs_plugin_customParseFormat = t();
	})(exports, (function() {
		"use strict";
		var e = {
			LTS: "h:mm:ss A",
			LT: "h:mm A",
			L: "MM/DD/YYYY",
			LL: "MMMM D, YYYY",
			LLL: "MMMM D, YYYY h:mm A",
			LLLL: "dddd, MMMM D, YYYY h:mm A"
		}, t = /(\[[^[]*\])|([-_:/.,()\s]+)|(A|a|Q|YYYY|YY?|ww?|MM?M?M?|Do|DD?|hh?|HH?|mm?|ss?|S{1,3}|z|ZZ?)/g, n = /\d/, r = /\d\d/, i = /\d\d?/, o = /\d*[^-_:/,()\s\d]+/, s = {}, a = function(e) {
			return (e = +e) + (e > 68 ? 1900 : 2e3);
		};
		var f = function(e) {
			return function(t) {
				this[e] = +t;
			};
		}, h = [/[+-]\d\d:?(\d\d)?|Z/, function(e) {
			(this.zone || (this.zone = {})).offset = function(e) {
				if (!e) return 0;
				if ("Z" === e) return 0;
				var t = e.match(/([+-]|\d\d)/g), n = 60 * t[1] + (+t[2] || 0);
				return 0 === n ? 0 : "+" === t[0] ? -n : n;
			}(e);
		}], u = function(e) {
			var t = s[e];
			return t && (t.indexOf ? t : t.s.concat(t.f));
		}, d = function(e, t) {
			var n, r = s.meridiem;
			if (r) {
				for (var i = 1; i <= 24; i += 1) if (e.indexOf(r(i, 0, t)) > -1) {
					n = i > 12;
					break;
				}
			} else n = e === (t ? "pm" : "PM");
			return n;
		}, c = {
			A: [o, function(e) {
				this.afternoon = d(e, !1);
			}],
			a: [o, function(e) {
				this.afternoon = d(e, !0);
			}],
			Q: [n, function(e) {
				this.month = 3 * (e - 1) + 1;
			}],
			S: [n, function(e) {
				this.milliseconds = 100 * +e;
			}],
			SS: [r, function(e) {
				this.milliseconds = 10 * +e;
			}],
			SSS: [/\d{3}/, function(e) {
				this.milliseconds = +e;
			}],
			s: [i, f("seconds")],
			ss: [i, f("seconds")],
			m: [i, f("minutes")],
			mm: [i, f("minutes")],
			H: [i, f("hours")],
			h: [i, f("hours")],
			HH: [i, f("hours")],
			hh: [i, f("hours")],
			D: [i, f("day")],
			DD: [r, f("day")],
			Do: [o, function(e) {
				var t = s.ordinal, n = e.match(/\d+/);
				if (this.day = n[0], t) for (var r = 1; r <= 31; r += 1) t(r).replace(/\[|\]/g, "") === e && (this.day = r);
			}],
			w: [i, f("week")],
			ww: [r, f("week")],
			M: [i, f("month")],
			MM: [r, f("month")],
			MMM: [o, function(e) {
				var t = u("months"), n = (u("monthsShort") || t.map((function(e) {
					return e.slice(0, 3);
				}))).indexOf(e) + 1;
				if (n < 1) throw new Error();
				this.month = n % 12 || n;
			}],
			MMMM: [o, function(e) {
				var t = u("months").indexOf(e) + 1;
				if (t < 1) throw new Error();
				this.month = t % 12 || t;
			}],
			Y: [/[+-]?\d+/, f("year")],
			YY: [r, function(e) {
				this.year = a(e);
			}],
			YYYY: [/\d{4}/, f("year")],
			Z: h,
			ZZ: h
		};
		function l(n) {
			var r = n, i = s && s.formats;
			for (var o = (n = r.replace(/(\[[^\]]+])|(LTS?|l{1,4}|L{1,4})/g, (function(t, n, r) {
				var o = r && r.toUpperCase();
				return n || i[r] || e[r] || i[o].replace(/(\[[^\]]+])|(MMMM|MM|DD|dddd)/g, (function(e, t, n) {
					return t || n.slice(1);
				}));
			}))).match(t), a = o.length, f = 0; f < a; f += 1) {
				var h = o[f], u = c[h], d = u && u[0], l = u && u[1];
				o[f] = l ? {
					regex: d,
					parser: l
				} : h.replace(/^\[|\]$/g, "");
			}
			return function(e) {
				for (var t = {}, n = 0, r = 0; n < a; n += 1) {
					var i = o[n];
					if ("string" == typeof i) r += i.length;
					else {
						var s = i.regex, f = i.parser, h = e.slice(r), u = s.exec(h)[0];
						f.call(t, u), e = e.replace(u, "");
					}
				}
				return function(e) {
					var t = e.afternoon;
					if (void 0 !== t) {
						var n = e.hours;
						t ? n < 12 && (e.hours += 12) : 12 === n && (e.hours = 0), delete e.afternoon;
					}
				}(t), t;
			};
		}
		return function(e, t, n) {
			n.p.customParseFormat = !0, e && e.parseTwoDigitYear && (a = e.parseTwoDigitYear);
			var r = t.prototype, i = r.parse;
			r.parse = function(e) {
				var t = e.date, r = e.utc, o = e.args;
				this.$u = r;
				var a = o[1];
				if ("string" == typeof a) {
					var f = !0 === o[2], h = !0 === o[3], u = f || h, d = o[2];
					h && (d = o[2]), s = this.$locale(), !f && d && (s = n.Ls[d]), this.$d = function(e, t, n, r) {
						try {
							if (["x", "X"].indexOf(t) > -1) return /* @__PURE__ */ new Date(("X" === t ? 1e3 : 1) * e);
							var i = l(t)(e), o = i.year, s = i.month, a = i.day, f = i.hours, h = i.minutes, u = i.seconds, d = i.milliseconds, c = i.zone, m = i.week, M = /* @__PURE__ */ new Date(), Y = a || (o || s ? 1 : M.getDate()), p = o || M.getFullYear(), v = 0;
							o && !s || (v = s > 0 ? s - 1 : M.getMonth());
							var D, w = f || 0, g = h || 0, y = u || 0, L = d || 0;
							return c ? new Date(Date.UTC(p, v, Y, w, g, y, L + 60 * c.offset * 1e3)) : n ? new Date(Date.UTC(p, v, Y, w, g, y, L)) : (D = new Date(p, v, Y, w, g, y, L), m && (D = r(D).week(m).toDate()), D);
						} catch (e) {
							return /* @__PURE__ */ new Date("");
						}
					}(t, a, r, n), this.init(), d && !0 !== d && (this.$L = this.locale(d).$L), u && t != this.format(a) && (this.$d = /* @__PURE__ */ new Date("")), s = {};
				} else if (a instanceof Array) for (var c = a.length, m = 1; m <= c; m += 1) {
					o[1] = a[m - 1];
					var M = n.apply(this, o);
					if (M.isValid()) {
						this.$d = M.$d, this.$L = M.$L, this.init();
						break;
					}
					m === c && (this.$d = /* @__PURE__ */ new Date(""));
				}
				else i.call(this, e);
			};
		};
	}));
}));
//#endregion
//#region node_modules/.bun/dayjs@1.11.20/node_modules/dayjs/plugin/advancedFormat.js
var require_advancedFormat = /* @__PURE__ */ __commonJSMin(((exports, module) => {
	(function(e, t) {
		"object" == typeof exports && "undefined" != typeof module ? module.exports = t() : "function" == typeof define && define.amd ? define(t) : (e = "undefined" != typeof globalThis ? globalThis : e || self).dayjs_plugin_advancedFormat = t();
	})(exports, (function() {
		"use strict";
		return function(e, t) {
			var r = t.prototype, n = r.format;
			r.format = function(e) {
				var t = this, r = this.$locale();
				if (!this.isValid()) return n.bind(this)(e);
				var s = this.$utils(), a = (e || "YYYY-MM-DDTHH:mm:ssZ").replace(/\[([^\]]+)]|Q|wo|ww|w|WW|W|zzz|z|gggg|GGGG|Do|X|x|k{1,2}|S/g, (function(e) {
					switch (e) {
						case "Q": return Math.ceil((t.$M + 1) / 3);
						case "Do": return r.ordinal(t.$D);
						case "gggg": return t.weekYear();
						case "GGGG": return t.isoWeekYear();
						case "wo": return r.ordinal(t.week(), "W");
						case "w":
						case "ww": return s.s(t.week(), "w" === e ? 1 : 2, "0");
						case "W":
						case "WW": return s.s(t.isoWeek(), "W" === e ? 1 : 2, "0");
						case "k":
						case "kk": return s.s(String(0 === t.$H ? 24 : t.$H), "k" === e ? 1 : 2, "0");
						case "X": return Math.floor(t.$d.getTime() / 1e3);
						case "x": return t.$d.getTime();
						case "z": return "[" + t.offsetName() + "]";
						case "zzz": return "[" + t.offsetName("long") + "]";
						default: return e;
					}
				}));
				return n.bind(this)(a);
			};
		};
	}));
}));
//#endregion
//#region node_modules/.bun/dayjs@1.11.20/node_modules/dayjs/plugin/duration.js
var require_duration = /* @__PURE__ */ __commonJSMin(((exports, module) => {
	(function(t, s) {
		"object" == typeof exports && "undefined" != typeof module ? module.exports = s() : "function" == typeof define && define.amd ? define(s) : (t = "undefined" != typeof globalThis ? globalThis : t || self).dayjs_plugin_duration = s();
	})(exports, (function() {
		"use strict";
		var t, s, n = 1e3, i = 6e4, e = 36e5, r = 864e5, o = /\[([^\]]+)]|Y{1,4}|M{1,4}|D{1,2}|d{1,4}|H{1,2}|h{1,2}|a|A|m{1,2}|s{1,2}|Z{1,2}|SSS/g, u = 31536e6, d = 2628e6, a = /^(-|\+)?P(?:([-+]?[0-9,.]*)Y)?(?:([-+]?[0-9,.]*)M)?(?:([-+]?[0-9,.]*)W)?(?:([-+]?[0-9,.]*)D)?(?:T(?:([-+]?[0-9,.]*)H)?(?:([-+]?[0-9,.]*)M)?(?:([-+]?[0-9,.]*)S)?)?$/, h = {
			years: u,
			months: d,
			days: r,
			hours: e,
			minutes: i,
			seconds: n,
			milliseconds: 1,
			weeks: 6048e5
		}, c = function(t) {
			return t instanceof g;
		}, f = function(t, s, n) {
			return new g(t, n, s.$l);
		}, m = function(t) {
			return s.p(t) + "s";
		}, l = function(t) {
			return t < 0;
		}, $ = function(t) {
			return l(t) ? Math.ceil(t) : Math.floor(t);
		}, y = function(t) {
			return Math.abs(t);
		}, v = function(t, s) {
			return t ? l(t) ? {
				negative: !0,
				format: "" + y(t) + s
			} : {
				negative: !1,
				format: "" + t + s
			} : {
				negative: !1,
				format: ""
			};
		}, g = function() {
			function l(t, s, n) {
				var i = this;
				if (this.$d = {}, this.$l = n, void 0 === t && (this.$ms = 0, this.parseFromMilliseconds()), s) return f(t * h[m(s)], this);
				if ("number" == typeof t) return this.$ms = t, this.parseFromMilliseconds(), this;
				if ("object" == typeof t) return Object.keys(t).forEach((function(s) {
					i.$d[m(s)] = t[s];
				})), this.calMilliseconds(), this;
				if ("string" == typeof t) {
					var e = t.match(a);
					if (e) {
						var r = e.slice(2).map((function(t) {
							return null != t ? Number(t) : 0;
						}));
						return this.$d.years = r[0], this.$d.months = r[1], this.$d.weeks = r[2], this.$d.days = r[3], this.$d.hours = r[4], this.$d.minutes = r[5], this.$d.seconds = r[6], this.calMilliseconds(), this;
					}
				}
				return this;
			}
			var y = l.prototype;
			return y.calMilliseconds = function() {
				var t = this;
				this.$ms = Object.keys(this.$d).reduce((function(s, n) {
					return s + (t.$d[n] || 0) * h[n];
				}), 0);
			}, y.parseFromMilliseconds = function() {
				var t = this.$ms;
				this.$d.years = $(t / u), t %= u, this.$d.months = $(t / d), t %= d, this.$d.days = $(t / r), t %= r, this.$d.hours = $(t / e), t %= e, this.$d.minutes = $(t / i), t %= i, this.$d.seconds = $(t / n), t %= n, this.$d.milliseconds = t;
			}, y.toISOString = function() {
				var t = v(this.$d.years, "Y"), s = v(this.$d.months, "M"), n = +this.$d.days || 0;
				this.$d.weeks && (n += 7 * this.$d.weeks);
				var i = v(n, "D"), e = v(this.$d.hours, "H"), r = v(this.$d.minutes, "M"), o = this.$d.seconds || 0;
				this.$d.milliseconds && (o += this.$d.milliseconds / 1e3, o = Math.round(1e3 * o) / 1e3);
				var u = v(o, "S"), d = t.negative || s.negative || i.negative || e.negative || r.negative || u.negative, a = e.format || r.format || u.format ? "T" : "", h = (d ? "-" : "") + "P" + t.format + s.format + i.format + a + e.format + r.format + u.format;
				return "P" === h || "-P" === h ? "P0D" : h;
			}, y.toJSON = function() {
				return this.toISOString();
			}, y.format = function(t) {
				var n = t || "YYYY-MM-DDTHH:mm:ss", i = {
					Y: this.$d.years,
					YY: s.s(this.$d.years, 2, "0"),
					YYYY: s.s(this.$d.years, 4, "0"),
					M: this.$d.months,
					MM: s.s(this.$d.months, 2, "0"),
					D: this.$d.days,
					DD: s.s(this.$d.days, 2, "0"),
					H: this.$d.hours,
					HH: s.s(this.$d.hours, 2, "0"),
					m: this.$d.minutes,
					mm: s.s(this.$d.minutes, 2, "0"),
					s: this.$d.seconds,
					ss: s.s(this.$d.seconds, 2, "0"),
					SSS: s.s(this.$d.milliseconds, 3, "0")
				};
				return n.replace(o, (function(t, s) {
					return s || String(i[t]);
				}));
			}, y.as = function(t) {
				return this.$ms / h[m(t)];
			}, y.get = function(t) {
				var s = this.$ms, n = m(t);
				return "milliseconds" === n ? s %= 1e3 : s = "weeks" === n ? $(s / h[n]) : this.$d[n], s || 0;
			}, y.add = function(t, s, n) {
				var i;
				return i = s ? t * h[m(s)] : c(t) ? t.$ms : f(t, this).$ms, f(this.$ms + i * (n ? -1 : 1), this);
			}, y.subtract = function(t, s) {
				return this.add(t, s, !0);
			}, y.locale = function(t) {
				var s = this.clone();
				return s.$l = t, s;
			}, y.clone = function() {
				return f(this.$ms, this);
			}, y.humanize = function(s) {
				return t().add(this.$ms, "ms").locale(this.$l).fromNow(!s);
			}, y.valueOf = function() {
				return this.asMilliseconds();
			}, y.milliseconds = function() {
				return this.get("milliseconds");
			}, y.asMilliseconds = function() {
				return this.as("milliseconds");
			}, y.seconds = function() {
				return this.get("seconds");
			}, y.asSeconds = function() {
				return this.as("seconds");
			}, y.minutes = function() {
				return this.get("minutes");
			}, y.asMinutes = function() {
				return this.as("minutes");
			}, y.hours = function() {
				return this.get("hours");
			}, y.asHours = function() {
				return this.as("hours");
			}, y.days = function() {
				return this.get("days");
			}, y.asDays = function() {
				return this.as("days");
			}, y.weeks = function() {
				return this.get("weeks");
			}, y.asWeeks = function() {
				return this.as("weeks");
			}, y.months = function() {
				return this.get("months");
			}, y.asMonths = function() {
				return this.as("months");
			}, y.years = function() {
				return this.get("years");
			}, y.asYears = function() {
				return this.as("years");
			}, l;
		}(), p = function(t, s, n) {
			return t.add(s.years() * n, "y").add(s.months() * n, "M").add(s.days() * n, "d").add(s.hours() * n, "h").add(s.minutes() * n, "m").add(s.seconds() * n, "s").add(s.milliseconds() * n, "ms");
		};
		return function(n, i, e) {
			t = e, s = e().$utils(), e.duration = function(t, s) {
				return f(t, { $l: e.locale() }, s);
			}, e.isDuration = c;
			var r = i.prototype.add, o = i.prototype.subtract;
			i.prototype.add = function(t, s) {
				return c(t) ? p(this, t, 1) : r.bind(this)(t, s);
			}, i.prototype.subtract = function(t, s) {
				return c(t) ? p(this, t, -1) : o.bind(this)(t, s);
			};
		};
	}));
}));
//#endregion
export { require_dayjs_min as a, require_isoWeek as i, require_advancedFormat as n, require_customParseFormat as r, require_duration as t };
