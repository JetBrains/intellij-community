import { n as Path } from "./d3-path.js";
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/constant.js
function constant_default(x) {
	return function constant() {
		return x;
	};
}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/math.js
var abs = Math.abs;
var atan2 = Math.atan2;
var cos = Math.cos;
var max = Math.max;
var min = Math.min;
var sin = Math.sin;
var sqrt = Math.sqrt;
var pi = Math.PI;
var halfPi = pi / 2;
var tau = 2 * pi;
function acos(x) {
	return x > 1 ? 0 : x < -1 ? pi : Math.acos(x);
}
function asin(x) {
	return x >= 1 ? halfPi : x <= -1 ? -halfPi : Math.asin(x);
}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/path.js
function withPath(shape) {
	let digits = 3;
	shape.digits = function(_) {
		if (!arguments.length) return digits;
		if (_ == null) digits = null;
		else {
			const d = Math.floor(_);
			if (!(d >= 0)) throw new RangeError(`invalid digits: ${_}`);
			digits = d;
		}
		return shape;
	};
	return () => new Path(digits);
}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/arc.js
function arcInnerRadius(d) {
	return d.innerRadius;
}
function arcOuterRadius(d) {
	return d.outerRadius;
}
function arcStartAngle(d) {
	return d.startAngle;
}
function arcEndAngle(d) {
	return d.endAngle;
}
function arcPadAngle(d) {
	return d && d.padAngle;
}
function intersect(x0, y0, x1, y1, x2, y2, x3, y3) {
	var x10 = x1 - x0, y10 = y1 - y0, x32 = x3 - x2, y32 = y3 - y2, t = y32 * x10 - x32 * y10;
	if (t * t < 1e-12) return;
	t = (x32 * (y0 - y2) - y32 * (x0 - x2)) / t;
	return [x0 + t * x10, y0 + t * y10];
}
function cornerTangents(x0, y0, x1, y1, r1, rc, cw) {
	var x01 = x0 - x1, y01 = y0 - y1, lo = (cw ? rc : -rc) / sqrt(x01 * x01 + y01 * y01), ox = lo * y01, oy = -lo * x01, x11 = x0 + ox, y11 = y0 + oy, x10 = x1 + ox, y10 = y1 + oy, x00 = (x11 + x10) / 2, y00 = (y11 + y10) / 2, dx = x10 - x11, dy = y10 - y11, d2 = dx * dx + dy * dy, r = r1 - rc, D = x11 * y10 - x10 * y11, d = (dy < 0 ? -1 : 1) * sqrt(max(0, r * r * d2 - D * D)), cx0 = (D * dy - dx * d) / d2, cy0 = (-D * dx - dy * d) / d2, cx1 = (D * dy + dx * d) / d2, cy1 = (-D * dx + dy * d) / d2, dx0 = cx0 - x00, dy0 = cy0 - y00, dx1 = cx1 - x00, dy1 = cy1 - y00;
	if (dx0 * dx0 + dy0 * dy0 > dx1 * dx1 + dy1 * dy1) cx0 = cx1, cy0 = cy1;
	return {
		cx: cx0,
		cy: cy0,
		x01: -ox,
		y01: -oy,
		x11: cx0 * (r1 / r - 1),
		y11: cy0 * (r1 / r - 1)
	};
}
function arc_default() {
	var innerRadius = arcInnerRadius, outerRadius = arcOuterRadius, cornerRadius = constant_default(0), padRadius = null, startAngle = arcStartAngle, endAngle = arcEndAngle, padAngle = arcPadAngle, context = null, path = withPath(arc);
	function arc() {
		var buffer, r, r0 = +innerRadius.apply(this, arguments), r1 = +outerRadius.apply(this, arguments), a0 = startAngle.apply(this, arguments) - halfPi, a1 = endAngle.apply(this, arguments) - halfPi, da = abs(a1 - a0), cw = a1 > a0;
		if (!context) context = buffer = path();
		if (r1 < r0) r = r1, r1 = r0, r0 = r;
		if (!(r1 > 1e-12)) context.moveTo(0, 0);
		else if (da > tau - 1e-12) {
			context.moveTo(r1 * cos(a0), r1 * sin(a0));
			context.arc(0, 0, r1, a0, a1, !cw);
			if (r0 > 1e-12) {
				context.moveTo(r0 * cos(a1), r0 * sin(a1));
				context.arc(0, 0, r0, a1, a0, cw);
			}
		} else {
			var a01 = a0, a11 = a1, a00 = a0, a10 = a1, da0 = da, da1 = da, ap = padAngle.apply(this, arguments) / 2, rp = ap > 1e-12 && (padRadius ? +padRadius.apply(this, arguments) : sqrt(r0 * r0 + r1 * r1)), rc = min(abs(r1 - r0) / 2, +cornerRadius.apply(this, arguments)), rc0 = rc, rc1 = rc, t0, t1;
			if (rp > 1e-12) {
				var p0 = asin(rp / r0 * sin(ap)), p1 = asin(rp / r1 * sin(ap));
				if ((da0 -= p0 * 2) > 1e-12) p0 *= cw ? 1 : -1, a00 += p0, a10 -= p0;
				else da0 = 0, a00 = a10 = (a0 + a1) / 2;
				if ((da1 -= p1 * 2) > 1e-12) p1 *= cw ? 1 : -1, a01 += p1, a11 -= p1;
				else da1 = 0, a01 = a11 = (a0 + a1) / 2;
			}
			var x01 = r1 * cos(a01), y01 = r1 * sin(a01), x10 = r0 * cos(a10), y10 = r0 * sin(a10);
			if (rc > 1e-12) {
				var x11 = r1 * cos(a11), y11 = r1 * sin(a11), x00 = r0 * cos(a00), y00 = r0 * sin(a00), oc;
				if (da < pi) if (oc = intersect(x01, y01, x00, y00, x11, y11, x10, y10)) {
					var ax = x01 - oc[0], ay = y01 - oc[1], bx = x11 - oc[0], by = y11 - oc[1], kc = 1 / sin(acos((ax * bx + ay * by) / (sqrt(ax * ax + ay * ay) * sqrt(bx * bx + by * by))) / 2), lc = sqrt(oc[0] * oc[0] + oc[1] * oc[1]);
					rc0 = min(rc, (r0 - lc) / (kc - 1));
					rc1 = min(rc, (r1 - lc) / (kc + 1));
				} else rc0 = rc1 = 0;
			}
			if (!(da1 > 1e-12)) context.moveTo(x01, y01);
			else if (rc1 > 1e-12) {
				t0 = cornerTangents(x00, y00, x01, y01, r1, rc1, cw);
				t1 = cornerTangents(x11, y11, x10, y10, r1, rc1, cw);
				context.moveTo(t0.cx + t0.x01, t0.cy + t0.y01);
				if (rc1 < rc) context.arc(t0.cx, t0.cy, rc1, atan2(t0.y01, t0.x01), atan2(t1.y01, t1.x01), !cw);
				else {
					context.arc(t0.cx, t0.cy, rc1, atan2(t0.y01, t0.x01), atan2(t0.y11, t0.x11), !cw);
					context.arc(0, 0, r1, atan2(t0.cy + t0.y11, t0.cx + t0.x11), atan2(t1.cy + t1.y11, t1.cx + t1.x11), !cw);
					context.arc(t1.cx, t1.cy, rc1, atan2(t1.y11, t1.x11), atan2(t1.y01, t1.x01), !cw);
				}
			} else context.moveTo(x01, y01), context.arc(0, 0, r1, a01, a11, !cw);
			if (!(r0 > 1e-12) || !(da0 > 1e-12)) context.lineTo(x10, y10);
			else if (rc0 > 1e-12) {
				t0 = cornerTangents(x10, y10, x11, y11, r0, -rc0, cw);
				t1 = cornerTangents(x01, y01, x00, y00, r0, -rc0, cw);
				context.lineTo(t0.cx + t0.x01, t0.cy + t0.y01);
				if (rc0 < rc) context.arc(t0.cx, t0.cy, rc0, atan2(t0.y01, t0.x01), atan2(t1.y01, t1.x01), !cw);
				else {
					context.arc(t0.cx, t0.cy, rc0, atan2(t0.y01, t0.x01), atan2(t0.y11, t0.x11), !cw);
					context.arc(0, 0, r0, atan2(t0.cy + t0.y11, t0.cx + t0.x11), atan2(t1.cy + t1.y11, t1.cx + t1.x11), cw);
					context.arc(t1.cx, t1.cy, rc0, atan2(t1.y11, t1.x11), atan2(t1.y01, t1.x01), !cw);
				}
			} else context.arc(0, 0, r0, a10, a00, cw);
		}
		context.closePath();
		if (buffer) return context = null, buffer + "" || null;
	}
	arc.centroid = function() {
		var r = (+innerRadius.apply(this, arguments) + +outerRadius.apply(this, arguments)) / 2, a = (+startAngle.apply(this, arguments) + +endAngle.apply(this, arguments)) / 2 - pi / 2;
		return [cos(a) * r, sin(a) * r];
	};
	arc.innerRadius = function(_) {
		return arguments.length ? (innerRadius = typeof _ === "function" ? _ : constant_default(+_), arc) : innerRadius;
	};
	arc.outerRadius = function(_) {
		return arguments.length ? (outerRadius = typeof _ === "function" ? _ : constant_default(+_), arc) : outerRadius;
	};
	arc.cornerRadius = function(_) {
		return arguments.length ? (cornerRadius = typeof _ === "function" ? _ : constant_default(+_), arc) : cornerRadius;
	};
	arc.padRadius = function(_) {
		return arguments.length ? (padRadius = _ == null ? null : typeof _ === "function" ? _ : constant_default(+_), arc) : padRadius;
	};
	arc.startAngle = function(_) {
		return arguments.length ? (startAngle = typeof _ === "function" ? _ : constant_default(+_), arc) : startAngle;
	};
	arc.endAngle = function(_) {
		return arguments.length ? (endAngle = typeof _ === "function" ? _ : constant_default(+_), arc) : endAngle;
	};
	arc.padAngle = function(_) {
		return arguments.length ? (padAngle = typeof _ === "function" ? _ : constant_default(+_), arc) : padAngle;
	};
	arc.context = function(_) {
		return arguments.length ? (context = _ == null ? null : _, arc) : context;
	};
	return arc;
}
Array.prototype.slice;
function array_default(x) {
	return typeof x === "object" && "length" in x ? x : Array.from(x);
}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/curve/linear.js
function Linear(context) {
	this._context = context;
}
Linear.prototype = {
	areaStart: function() {
		this._line = 0;
	},
	areaEnd: function() {
		this._line = NaN;
	},
	lineStart: function() {
		this._point = 0;
	},
	lineEnd: function() {
		if (this._line || this._line !== 0 && this._point === 1) this._context.closePath();
		this._line = 1 - this._line;
	},
	point: function(x, y) {
		x = +x, y = +y;
		switch (this._point) {
			case 0:
				this._point = 1;
				this._line ? this._context.lineTo(x, y) : this._context.moveTo(x, y);
				break;
			case 1: this._point = 2;
			default:
				this._context.lineTo(x, y);
				break;
		}
	}
};
function linear_default(context) {
	return new Linear(context);
}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/point.js
function x(p) {
	return p[0];
}
function y(p) {
	return p[1];
}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/line.js
function line_default(x$1, y$1) {
	var defined = constant_default(true), context = null, curve = linear_default, output = null, path = withPath(line);
	x$1 = typeof x$1 === "function" ? x$1 : x$1 === void 0 ? x : constant_default(x$1);
	y$1 = typeof y$1 === "function" ? y$1 : y$1 === void 0 ? y : constant_default(y$1);
	function line(data) {
		var i, n = (data = array_default(data)).length, d, defined0 = false, buffer;
		if (context == null) output = curve(buffer = path());
		for (i = 0; i <= n; ++i) {
			if (!(i < n && defined(d = data[i], i, data)) === defined0) if (defined0 = !defined0) output.lineStart();
			else output.lineEnd();
			if (defined0) output.point(+x$1(d, i, data), +y$1(d, i, data));
		}
		if (buffer) return output = null, buffer + "" || null;
	}
	line.x = function(_) {
		return arguments.length ? (x$1 = typeof _ === "function" ? _ : constant_default(+_), line) : x$1;
	};
	line.y = function(_) {
		return arguments.length ? (y$1 = typeof _ === "function" ? _ : constant_default(+_), line) : y$1;
	};
	line.defined = function(_) {
		return arguments.length ? (defined = typeof _ === "function" ? _ : constant_default(!!_), line) : defined;
	};
	line.curve = function(_) {
		return arguments.length ? (curve = _, context != null && (output = curve(context)), line) : curve;
	};
	line.context = function(_) {
		return arguments.length ? (_ == null ? context = output = null : output = curve(context = _), line) : context;
	};
	return line;
}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/descending.js
function descending_default(a, b) {
	return b < a ? -1 : b > a ? 1 : b >= a ? 0 : NaN;
}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/identity.js
function identity_default(d) {
	return d;
}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/pie.js
function pie_default() {
	var value = identity_default, sortValues = descending_default, sort = null, startAngle = constant_default(0), endAngle = constant_default(tau), padAngle = constant_default(0);
	function pie(data) {
		var i, n = (data = array_default(data)).length, j, k, sum = 0, index = new Array(n), arcs = new Array(n), a0 = +startAngle.apply(this, arguments), da = Math.min(tau, Math.max(-tau, endAngle.apply(this, arguments) - a0)), a1, p = Math.min(Math.abs(da) / n, padAngle.apply(this, arguments)), pa = p * (da < 0 ? -1 : 1), v;
		for (i = 0; i < n; ++i) if ((v = arcs[index[i] = i] = +value(data[i], i, data)) > 0) sum += v;
		if (sortValues != null) index.sort(function(i, j) {
			return sortValues(arcs[i], arcs[j]);
		});
		else if (sort != null) index.sort(function(i, j) {
			return sort(data[i], data[j]);
		});
		for (i = 0, k = sum ? (da - n * pa) / sum : 0; i < n; ++i, a0 = a1) j = index[i], v = arcs[j], a1 = a0 + (v > 0 ? v * k : 0) + pa, arcs[j] = {
			data: data[j],
			index: i,
			value: v,
			startAngle: a0,
			endAngle: a1,
			padAngle: p
		};
		return arcs;
	}
	pie.value = function(_) {
		return arguments.length ? (value = typeof _ === "function" ? _ : constant_default(+_), pie) : value;
	};
	pie.sortValues = function(_) {
		return arguments.length ? (sortValues = _, sort = null, pie) : sortValues;
	};
	pie.sort = function(_) {
		return arguments.length ? (sort = _, sortValues = null, pie) : sort;
	};
	pie.startAngle = function(_) {
		return arguments.length ? (startAngle = typeof _ === "function" ? _ : constant_default(+_), pie) : startAngle;
	};
	pie.endAngle = function(_) {
		return arguments.length ? (endAngle = typeof _ === "function" ? _ : constant_default(+_), pie) : endAngle;
	};
	pie.padAngle = function(_) {
		return arguments.length ? (padAngle = typeof _ === "function" ? _ : constant_default(+_), pie) : padAngle;
	};
	return pie;
}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/curve/bump.js
var Bump = class {
	constructor(context, x) {
		this._context = context;
		this._x = x;
	}
	areaStart() {
		this._line = 0;
	}
	areaEnd() {
		this._line = NaN;
	}
	lineStart() {
		this._point = 0;
	}
	lineEnd() {
		if (this._line || this._line !== 0 && this._point === 1) this._context.closePath();
		this._line = 1 - this._line;
	}
	point(x, y) {
		x = +x, y = +y;
		switch (this._point) {
			case 0:
				this._point = 1;
				if (this._line) this._context.lineTo(x, y);
				else this._context.moveTo(x, y);
				break;
			case 1: this._point = 2;
			default:
				if (this._x) this._context.bezierCurveTo(this._x0 = (this._x0 + x) / 2, this._y0, this._x0, y, x, y);
				else this._context.bezierCurveTo(this._x0, this._y0 = (this._y0 + y) / 2, x, this._y0, x, y);
				break;
		}
		this._x0 = x, this._y0 = y;
	}
};
function bumpX(context) {
	return new Bump(context, true);
}
function bumpY(context) {
	return new Bump(context, false);
}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/noop.js
function noop_default() {}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/curve/basis.js
function point$3(that, x, y) {
	that._context.bezierCurveTo((2 * that._x0 + that._x1) / 3, (2 * that._y0 + that._y1) / 3, (that._x0 + 2 * that._x1) / 3, (that._y0 + 2 * that._y1) / 3, (that._x0 + 4 * that._x1 + x) / 6, (that._y0 + 4 * that._y1 + y) / 6);
}
function Basis(context) {
	this._context = context;
}
Basis.prototype = {
	areaStart: function() {
		this._line = 0;
	},
	areaEnd: function() {
		this._line = NaN;
	},
	lineStart: function() {
		this._x0 = this._x1 = this._y0 = this._y1 = NaN;
		this._point = 0;
	},
	lineEnd: function() {
		switch (this._point) {
			case 3: point$3(this, this._x1, this._y1);
			case 2:
				this._context.lineTo(this._x1, this._y1);
				break;
		}
		if (this._line || this._line !== 0 && this._point === 1) this._context.closePath();
		this._line = 1 - this._line;
	},
	point: function(x, y) {
		x = +x, y = +y;
		switch (this._point) {
			case 0:
				this._point = 1;
				this._line ? this._context.lineTo(x, y) : this._context.moveTo(x, y);
				break;
			case 1:
				this._point = 2;
				break;
			case 2:
				this._point = 3;
				this._context.lineTo((5 * this._x0 + this._x1) / 6, (5 * this._y0 + this._y1) / 6);
			default:
				point$3(this, x, y);
				break;
		}
		this._x0 = this._x1, this._x1 = x;
		this._y0 = this._y1, this._y1 = y;
	}
};
function basis_default(context) {
	return new Basis(context);
}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/curve/basisClosed.js
function BasisClosed(context) {
	this._context = context;
}
BasisClosed.prototype = {
	areaStart: noop_default,
	areaEnd: noop_default,
	lineStart: function() {
		this._x0 = this._x1 = this._x2 = this._x3 = this._x4 = this._y0 = this._y1 = this._y2 = this._y3 = this._y4 = NaN;
		this._point = 0;
	},
	lineEnd: function() {
		switch (this._point) {
			case 1:
				this._context.moveTo(this._x2, this._y2);
				this._context.closePath();
				break;
			case 2:
				this._context.moveTo((this._x2 + 2 * this._x3) / 3, (this._y2 + 2 * this._y3) / 3);
				this._context.lineTo((this._x3 + 2 * this._x2) / 3, (this._y3 + 2 * this._y2) / 3);
				this._context.closePath();
				break;
			case 3:
				this.point(this._x2, this._y2);
				this.point(this._x3, this._y3);
				this.point(this._x4, this._y4);
				break;
		}
	},
	point: function(x, y) {
		x = +x, y = +y;
		switch (this._point) {
			case 0:
				this._point = 1;
				this._x2 = x, this._y2 = y;
				break;
			case 1:
				this._point = 2;
				this._x3 = x, this._y3 = y;
				break;
			case 2:
				this._point = 3;
				this._x4 = x, this._y4 = y;
				this._context.moveTo((this._x0 + 4 * this._x1 + x) / 6, (this._y0 + 4 * this._y1 + y) / 6);
				break;
			default:
				point$3(this, x, y);
				break;
		}
		this._x0 = this._x1, this._x1 = x;
		this._y0 = this._y1, this._y1 = y;
	}
};
function basisClosed_default(context) {
	return new BasisClosed(context);
}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/curve/basisOpen.js
function BasisOpen(context) {
	this._context = context;
}
BasisOpen.prototype = {
	areaStart: function() {
		this._line = 0;
	},
	areaEnd: function() {
		this._line = NaN;
	},
	lineStart: function() {
		this._x0 = this._x1 = this._y0 = this._y1 = NaN;
		this._point = 0;
	},
	lineEnd: function() {
		if (this._line || this._line !== 0 && this._point === 3) this._context.closePath();
		this._line = 1 - this._line;
	},
	point: function(x, y) {
		x = +x, y = +y;
		switch (this._point) {
			case 0:
				this._point = 1;
				break;
			case 1:
				this._point = 2;
				break;
			case 2:
				this._point = 3;
				var x0 = (this._x0 + 4 * this._x1 + x) / 6, y0 = (this._y0 + 4 * this._y1 + y) / 6;
				this._line ? this._context.lineTo(x0, y0) : this._context.moveTo(x0, y0);
				break;
			case 3: this._point = 4;
			default:
				point$3(this, x, y);
				break;
		}
		this._x0 = this._x1, this._x1 = x;
		this._y0 = this._y1, this._y1 = y;
	}
};
function basisOpen_default(context) {
	return new BasisOpen(context);
}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/curve/bundle.js
function Bundle(context, beta) {
	this._basis = new Basis(context);
	this._beta = beta;
}
Bundle.prototype = {
	lineStart: function() {
		this._x = [];
		this._y = [];
		this._basis.lineStart();
	},
	lineEnd: function() {
		var x = this._x, y = this._y, j = x.length - 1;
		if (j > 0) {
			var x0 = x[0], y0 = y[0], dx = x[j] - x0, dy = y[j] - y0, i = -1, t;
			while (++i <= j) {
				t = i / j;
				this._basis.point(this._beta * x[i] + (1 - this._beta) * (x0 + t * dx), this._beta * y[i] + (1 - this._beta) * (y0 + t * dy));
			}
		}
		this._x = this._y = null;
		this._basis.lineEnd();
	},
	point: function(x, y) {
		this._x.push(+x);
		this._y.push(+y);
	}
};
var bundle_default = (function custom(beta) {
	function bundle(context) {
		return beta === 1 ? new Basis(context) : new Bundle(context, beta);
	}
	bundle.beta = function(beta) {
		return custom(+beta);
	};
	return bundle;
})(.85);
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/curve/cardinal.js
function point$2(that, x, y) {
	that._context.bezierCurveTo(that._x1 + that._k * (that._x2 - that._x0), that._y1 + that._k * (that._y2 - that._y0), that._x2 + that._k * (that._x1 - x), that._y2 + that._k * (that._y1 - y), that._x2, that._y2);
}
function Cardinal(context, tension) {
	this._context = context;
	this._k = (1 - tension) / 6;
}
Cardinal.prototype = {
	areaStart: function() {
		this._line = 0;
	},
	areaEnd: function() {
		this._line = NaN;
	},
	lineStart: function() {
		this._x0 = this._x1 = this._x2 = this._y0 = this._y1 = this._y2 = NaN;
		this._point = 0;
	},
	lineEnd: function() {
		switch (this._point) {
			case 2:
				this._context.lineTo(this._x2, this._y2);
				break;
			case 3:
				point$2(this, this._x1, this._y1);
				break;
		}
		if (this._line || this._line !== 0 && this._point === 1) this._context.closePath();
		this._line = 1 - this._line;
	},
	point: function(x, y) {
		x = +x, y = +y;
		switch (this._point) {
			case 0:
				this._point = 1;
				this._line ? this._context.lineTo(x, y) : this._context.moveTo(x, y);
				break;
			case 1:
				this._point = 2;
				this._x1 = x, this._y1 = y;
				break;
			case 2: this._point = 3;
			default:
				point$2(this, x, y);
				break;
		}
		this._x0 = this._x1, this._x1 = this._x2, this._x2 = x;
		this._y0 = this._y1, this._y1 = this._y2, this._y2 = y;
	}
};
var cardinal_default = (function custom(tension) {
	function cardinal(context) {
		return new Cardinal(context, tension);
	}
	cardinal.tension = function(tension) {
		return custom(+tension);
	};
	return cardinal;
})(0);
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/curve/cardinalClosed.js
function CardinalClosed(context, tension) {
	this._context = context;
	this._k = (1 - tension) / 6;
}
CardinalClosed.prototype = {
	areaStart: noop_default,
	areaEnd: noop_default,
	lineStart: function() {
		this._x0 = this._x1 = this._x2 = this._x3 = this._x4 = this._x5 = this._y0 = this._y1 = this._y2 = this._y3 = this._y4 = this._y5 = NaN;
		this._point = 0;
	},
	lineEnd: function() {
		switch (this._point) {
			case 1:
				this._context.moveTo(this._x3, this._y3);
				this._context.closePath();
				break;
			case 2:
				this._context.lineTo(this._x3, this._y3);
				this._context.closePath();
				break;
			case 3:
				this.point(this._x3, this._y3);
				this.point(this._x4, this._y4);
				this.point(this._x5, this._y5);
				break;
		}
	},
	point: function(x, y) {
		x = +x, y = +y;
		switch (this._point) {
			case 0:
				this._point = 1;
				this._x3 = x, this._y3 = y;
				break;
			case 1:
				this._point = 2;
				this._context.moveTo(this._x4 = x, this._y4 = y);
				break;
			case 2:
				this._point = 3;
				this._x5 = x, this._y5 = y;
				break;
			default:
				point$2(this, x, y);
				break;
		}
		this._x0 = this._x1, this._x1 = this._x2, this._x2 = x;
		this._y0 = this._y1, this._y1 = this._y2, this._y2 = y;
	}
};
var cardinalClosed_default = (function custom(tension) {
	function cardinal(context) {
		return new CardinalClosed(context, tension);
	}
	cardinal.tension = function(tension) {
		return custom(+tension);
	};
	return cardinal;
})(0);
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/curve/cardinalOpen.js
function CardinalOpen(context, tension) {
	this._context = context;
	this._k = (1 - tension) / 6;
}
CardinalOpen.prototype = {
	areaStart: function() {
		this._line = 0;
	},
	areaEnd: function() {
		this._line = NaN;
	},
	lineStart: function() {
		this._x0 = this._x1 = this._x2 = this._y0 = this._y1 = this._y2 = NaN;
		this._point = 0;
	},
	lineEnd: function() {
		if (this._line || this._line !== 0 && this._point === 3) this._context.closePath();
		this._line = 1 - this._line;
	},
	point: function(x, y) {
		x = +x, y = +y;
		switch (this._point) {
			case 0:
				this._point = 1;
				break;
			case 1:
				this._point = 2;
				break;
			case 2:
				this._point = 3;
				this._line ? this._context.lineTo(this._x2, this._y2) : this._context.moveTo(this._x2, this._y2);
				break;
			case 3: this._point = 4;
			default:
				point$2(this, x, y);
				break;
		}
		this._x0 = this._x1, this._x1 = this._x2, this._x2 = x;
		this._y0 = this._y1, this._y1 = this._y2, this._y2 = y;
	}
};
var cardinalOpen_default = (function custom(tension) {
	function cardinal(context) {
		return new CardinalOpen(context, tension);
	}
	cardinal.tension = function(tension) {
		return custom(+tension);
	};
	return cardinal;
})(0);
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/curve/catmullRom.js
function point$1(that, x, y) {
	var x1 = that._x1, y1 = that._y1, x2 = that._x2, y2 = that._y2;
	if (that._l01_a > 1e-12) {
		var a = 2 * that._l01_2a + 3 * that._l01_a * that._l12_a + that._l12_2a, n = 3 * that._l01_a * (that._l01_a + that._l12_a);
		x1 = (x1 * a - that._x0 * that._l12_2a + that._x2 * that._l01_2a) / n;
		y1 = (y1 * a - that._y0 * that._l12_2a + that._y2 * that._l01_2a) / n;
	}
	if (that._l23_a > 1e-12) {
		var b = 2 * that._l23_2a + 3 * that._l23_a * that._l12_a + that._l12_2a, m = 3 * that._l23_a * (that._l23_a + that._l12_a);
		x2 = (x2 * b + that._x1 * that._l23_2a - x * that._l12_2a) / m;
		y2 = (y2 * b + that._y1 * that._l23_2a - y * that._l12_2a) / m;
	}
	that._context.bezierCurveTo(x1, y1, x2, y2, that._x2, that._y2);
}
function CatmullRom(context, alpha) {
	this._context = context;
	this._alpha = alpha;
}
CatmullRom.prototype = {
	areaStart: function() {
		this._line = 0;
	},
	areaEnd: function() {
		this._line = NaN;
	},
	lineStart: function() {
		this._x0 = this._x1 = this._x2 = this._y0 = this._y1 = this._y2 = NaN;
		this._l01_a = this._l12_a = this._l23_a = this._l01_2a = this._l12_2a = this._l23_2a = this._point = 0;
	},
	lineEnd: function() {
		switch (this._point) {
			case 2:
				this._context.lineTo(this._x2, this._y2);
				break;
			case 3:
				this.point(this._x2, this._y2);
				break;
		}
		if (this._line || this._line !== 0 && this._point === 1) this._context.closePath();
		this._line = 1 - this._line;
	},
	point: function(x, y) {
		x = +x, y = +y;
		if (this._point) {
			var x23 = this._x2 - x, y23 = this._y2 - y;
			this._l23_a = Math.sqrt(this._l23_2a = Math.pow(x23 * x23 + y23 * y23, this._alpha));
		}
		switch (this._point) {
			case 0:
				this._point = 1;
				this._line ? this._context.lineTo(x, y) : this._context.moveTo(x, y);
				break;
			case 1:
				this._point = 2;
				break;
			case 2: this._point = 3;
			default:
				point$1(this, x, y);
				break;
		}
		this._l01_a = this._l12_a, this._l12_a = this._l23_a;
		this._l01_2a = this._l12_2a, this._l12_2a = this._l23_2a;
		this._x0 = this._x1, this._x1 = this._x2, this._x2 = x;
		this._y0 = this._y1, this._y1 = this._y2, this._y2 = y;
	}
};
var catmullRom_default = (function custom(alpha) {
	function catmullRom(context) {
		return alpha ? new CatmullRom(context, alpha) : new Cardinal(context, 0);
	}
	catmullRom.alpha = function(alpha) {
		return custom(+alpha);
	};
	return catmullRom;
})(.5);
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/curve/catmullRomClosed.js
function CatmullRomClosed(context, alpha) {
	this._context = context;
	this._alpha = alpha;
}
CatmullRomClosed.prototype = {
	areaStart: noop_default,
	areaEnd: noop_default,
	lineStart: function() {
		this._x0 = this._x1 = this._x2 = this._x3 = this._x4 = this._x5 = this._y0 = this._y1 = this._y2 = this._y3 = this._y4 = this._y5 = NaN;
		this._l01_a = this._l12_a = this._l23_a = this._l01_2a = this._l12_2a = this._l23_2a = this._point = 0;
	},
	lineEnd: function() {
		switch (this._point) {
			case 1:
				this._context.moveTo(this._x3, this._y3);
				this._context.closePath();
				break;
			case 2:
				this._context.lineTo(this._x3, this._y3);
				this._context.closePath();
				break;
			case 3:
				this.point(this._x3, this._y3);
				this.point(this._x4, this._y4);
				this.point(this._x5, this._y5);
				break;
		}
	},
	point: function(x, y) {
		x = +x, y = +y;
		if (this._point) {
			var x23 = this._x2 - x, y23 = this._y2 - y;
			this._l23_a = Math.sqrt(this._l23_2a = Math.pow(x23 * x23 + y23 * y23, this._alpha));
		}
		switch (this._point) {
			case 0:
				this._point = 1;
				this._x3 = x, this._y3 = y;
				break;
			case 1:
				this._point = 2;
				this._context.moveTo(this._x4 = x, this._y4 = y);
				break;
			case 2:
				this._point = 3;
				this._x5 = x, this._y5 = y;
				break;
			default:
				point$1(this, x, y);
				break;
		}
		this._l01_a = this._l12_a, this._l12_a = this._l23_a;
		this._l01_2a = this._l12_2a, this._l12_2a = this._l23_2a;
		this._x0 = this._x1, this._x1 = this._x2, this._x2 = x;
		this._y0 = this._y1, this._y1 = this._y2, this._y2 = y;
	}
};
var catmullRomClosed_default = (function custom(alpha) {
	function catmullRom(context) {
		return alpha ? new CatmullRomClosed(context, alpha) : new CardinalClosed(context, 0);
	}
	catmullRom.alpha = function(alpha) {
		return custom(+alpha);
	};
	return catmullRom;
})(.5);
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/curve/catmullRomOpen.js
function CatmullRomOpen(context, alpha) {
	this._context = context;
	this._alpha = alpha;
}
CatmullRomOpen.prototype = {
	areaStart: function() {
		this._line = 0;
	},
	areaEnd: function() {
		this._line = NaN;
	},
	lineStart: function() {
		this._x0 = this._x1 = this._x2 = this._y0 = this._y1 = this._y2 = NaN;
		this._l01_a = this._l12_a = this._l23_a = this._l01_2a = this._l12_2a = this._l23_2a = this._point = 0;
	},
	lineEnd: function() {
		if (this._line || this._line !== 0 && this._point === 3) this._context.closePath();
		this._line = 1 - this._line;
	},
	point: function(x, y) {
		x = +x, y = +y;
		if (this._point) {
			var x23 = this._x2 - x, y23 = this._y2 - y;
			this._l23_a = Math.sqrt(this._l23_2a = Math.pow(x23 * x23 + y23 * y23, this._alpha));
		}
		switch (this._point) {
			case 0:
				this._point = 1;
				break;
			case 1:
				this._point = 2;
				break;
			case 2:
				this._point = 3;
				this._line ? this._context.lineTo(this._x2, this._y2) : this._context.moveTo(this._x2, this._y2);
				break;
			case 3: this._point = 4;
			default:
				point$1(this, x, y);
				break;
		}
		this._l01_a = this._l12_a, this._l12_a = this._l23_a;
		this._l01_2a = this._l12_2a, this._l12_2a = this._l23_2a;
		this._x0 = this._x1, this._x1 = this._x2, this._x2 = x;
		this._y0 = this._y1, this._y1 = this._y2, this._y2 = y;
	}
};
var catmullRomOpen_default = (function custom(alpha) {
	function catmullRom(context) {
		return alpha ? new CatmullRomOpen(context, alpha) : new CardinalOpen(context, 0);
	}
	catmullRom.alpha = function(alpha) {
		return custom(+alpha);
	};
	return catmullRom;
})(.5);
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/curve/linearClosed.js
function LinearClosed(context) {
	this._context = context;
}
LinearClosed.prototype = {
	areaStart: noop_default,
	areaEnd: noop_default,
	lineStart: function() {
		this._point = 0;
	},
	lineEnd: function() {
		if (this._point) this._context.closePath();
	},
	point: function(x, y) {
		x = +x, y = +y;
		if (this._point) this._context.lineTo(x, y);
		else this._point = 1, this._context.moveTo(x, y);
	}
};
function linearClosed_default(context) {
	return new LinearClosed(context);
}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/curve/monotone.js
function sign(x) {
	return x < 0 ? -1 : 1;
}
function slope3(that, x2, y2) {
	var h0 = that._x1 - that._x0, h1 = x2 - that._x1, s0 = (that._y1 - that._y0) / (h0 || h1 < 0 && -0), s1 = (y2 - that._y1) / (h1 || h0 < 0 && -0), p = (s0 * h1 + s1 * h0) / (h0 + h1);
	return (sign(s0) + sign(s1)) * Math.min(Math.abs(s0), Math.abs(s1), .5 * Math.abs(p)) || 0;
}
function slope2(that, t) {
	var h = that._x1 - that._x0;
	return h ? (3 * (that._y1 - that._y0) / h - t) / 2 : t;
}
function point(that, t0, t1) {
	var x0 = that._x0, y0 = that._y0, x1 = that._x1, y1 = that._y1, dx = (x1 - x0) / 3;
	that._context.bezierCurveTo(x0 + dx, y0 + dx * t0, x1 - dx, y1 - dx * t1, x1, y1);
}
function MonotoneX(context) {
	this._context = context;
}
MonotoneX.prototype = {
	areaStart: function() {
		this._line = 0;
	},
	areaEnd: function() {
		this._line = NaN;
	},
	lineStart: function() {
		this._x0 = this._x1 = this._y0 = this._y1 = this._t0 = NaN;
		this._point = 0;
	},
	lineEnd: function() {
		switch (this._point) {
			case 2:
				this._context.lineTo(this._x1, this._y1);
				break;
			case 3:
				point(this, this._t0, slope2(this, this._t0));
				break;
		}
		if (this._line || this._line !== 0 && this._point === 1) this._context.closePath();
		this._line = 1 - this._line;
	},
	point: function(x, y) {
		var t1 = NaN;
		x = +x, y = +y;
		if (x === this._x1 && y === this._y1) return;
		switch (this._point) {
			case 0:
				this._point = 1;
				this._line ? this._context.lineTo(x, y) : this._context.moveTo(x, y);
				break;
			case 1:
				this._point = 2;
				break;
			case 2:
				this._point = 3;
				point(this, slope2(this, t1 = slope3(this, x, y)), t1);
				break;
			default:
				point(this, this._t0, t1 = slope3(this, x, y));
				break;
		}
		this._x0 = this._x1, this._x1 = x;
		this._y0 = this._y1, this._y1 = y;
		this._t0 = t1;
	}
};
function MonotoneY(context) {
	this._context = new ReflectContext(context);
}
(MonotoneY.prototype = Object.create(MonotoneX.prototype)).point = function(x, y) {
	MonotoneX.prototype.point.call(this, y, x);
};
function ReflectContext(context) {
	this._context = context;
}
ReflectContext.prototype = {
	moveTo: function(x, y) {
		this._context.moveTo(y, x);
	},
	closePath: function() {
		this._context.closePath();
	},
	lineTo: function(x, y) {
		this._context.lineTo(y, x);
	},
	bezierCurveTo: function(x1, y1, x2, y2, x, y) {
		this._context.bezierCurveTo(y1, x1, y2, x2, y, x);
	}
};
function monotoneX(context) {
	return new MonotoneX(context);
}
function monotoneY(context) {
	return new MonotoneY(context);
}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/curve/natural.js
function Natural(context) {
	this._context = context;
}
Natural.prototype = {
	areaStart: function() {
		this._line = 0;
	},
	areaEnd: function() {
		this._line = NaN;
	},
	lineStart: function() {
		this._x = [];
		this._y = [];
	},
	lineEnd: function() {
		var x = this._x, y = this._y, n = x.length;
		if (n) {
			this._line ? this._context.lineTo(x[0], y[0]) : this._context.moveTo(x[0], y[0]);
			if (n === 2) this._context.lineTo(x[1], y[1]);
			else {
				var px = controlPoints(x), py = controlPoints(y);
				for (var i0 = 0, i1 = 1; i1 < n; ++i0, ++i1) this._context.bezierCurveTo(px[0][i0], py[0][i0], px[1][i0], py[1][i0], x[i1], y[i1]);
			}
		}
		if (this._line || this._line !== 0 && n === 1) this._context.closePath();
		this._line = 1 - this._line;
		this._x = this._y = null;
	},
	point: function(x, y) {
		this._x.push(+x);
		this._y.push(+y);
	}
};
function controlPoints(x) {
	var i, n = x.length - 1, m, a = new Array(n), b = new Array(n), r = new Array(n);
	a[0] = 0, b[0] = 2, r[0] = x[0] + 2 * x[1];
	for (i = 1; i < n - 1; ++i) a[i] = 1, b[i] = 4, r[i] = 4 * x[i] + 2 * x[i + 1];
	a[n - 1] = 2, b[n - 1] = 7, r[n - 1] = 8 * x[n - 1] + x[n];
	for (i = 1; i < n; ++i) m = a[i] / b[i - 1], b[i] -= m, r[i] -= m * r[i - 1];
	a[n - 1] = r[n - 1] / b[n - 1];
	for (i = n - 2; i >= 0; --i) a[i] = (r[i] - a[i + 1]) / b[i];
	b[n - 1] = (x[n] + a[n - 1]) / 2;
	for (i = 0; i < n - 1; ++i) b[i] = 2 * x[i + 1] - a[i + 1];
	return [a, b];
}
function natural_default(context) {
	return new Natural(context);
}
//#endregion
//#region node_modules/.bun/d3-shape@3.2.0/node_modules/d3-shape/src/curve/step.js
function Step(context, t) {
	this._context = context;
	this._t = t;
}
Step.prototype = {
	areaStart: function() {
		this._line = 0;
	},
	areaEnd: function() {
		this._line = NaN;
	},
	lineStart: function() {
		this._x = this._y = NaN;
		this._point = 0;
	},
	lineEnd: function() {
		if (0 < this._t && this._t < 1 && this._point === 2) this._context.lineTo(this._x, this._y);
		if (this._line || this._line !== 0 && this._point === 1) this._context.closePath();
		if (this._line >= 0) this._t = 1 - this._t, this._line = 1 - this._line;
	},
	point: function(x, y) {
		x = +x, y = +y;
		switch (this._point) {
			case 0:
				this._point = 1;
				this._line ? this._context.lineTo(x, y) : this._context.moveTo(x, y);
				break;
			case 1: this._point = 2;
			default:
				if (this._t <= 0) {
					this._context.lineTo(this._x, y);
					this._context.lineTo(x, y);
				} else {
					var x1 = this._x * (1 - this._t) + x * this._t;
					this._context.lineTo(x1, this._y);
					this._context.lineTo(x1, y);
				}
				break;
		}
		this._x = x, this._y = y;
	}
};
function step_default(context) {
	return new Step(context, .5);
}
function stepBefore(context) {
	return new Step(context, 0);
}
function stepAfter(context) {
	return new Step(context, 1);
}
//#endregion
export { arc_default as C, linear_default as S, basis_default as _, monotoneX as a, pie_default as b, catmullRomOpen_default as c, cardinalOpen_default as d, cardinalClosed_default as f, basisClosed_default as g, basisOpen_default as h, natural_default as i, catmullRomClosed_default as l, bundle_default as m, stepBefore as n, monotoneY as o, cardinal_default as p, step_default as r, linearClosed_default as s, stepAfter as t, catmullRom_default as u, bumpX as v, line_default as x, bumpY as y };
