var pi$1 = Math.PI, tau$1 = 2 * pi$1, epsilon$1 = 1e-6, tauEpsilon$1 = tau$1 - epsilon$1;
function append(strings) {
	this._ += strings[0];
	for (let i = 1, n = strings.length; i < n; ++i) this._ += arguments[i] + strings[i];
}
function appendRound(digits) {
	let d = Math.floor(digits);
	if (!(d >= 0)) throw new Error(`invalid digits: ${digits}`);
	if (d > 15) return append;
	const k = 10 ** d;
	return function(strings) {
		this._ += strings[0];
		for (let i = 1, n = strings.length; i < n; ++i) this._ += Math.round(arguments[i] * k) / k + strings[i];
	};
}
var Path$1 = class {
	constructor(digits) {
		this._x0 = this._y0 = this._x1 = this._y1 = null;
		this._ = "";
		this._append = digits == null ? append : appendRound(digits);
	}
	moveTo(x, y) {
		this._append`M${this._x0 = this._x1 = +x},${this._y0 = this._y1 = +y}`;
	}
	closePath() {
		if (this._x1 !== null) {
			this._x1 = this._x0, this._y1 = this._y0;
			this._append`Z`;
		}
	}
	lineTo(x, y) {
		this._append`L${this._x1 = +x},${this._y1 = +y}`;
	}
	quadraticCurveTo(x1, y1, x, y) {
		this._append`Q${+x1},${+y1},${this._x1 = +x},${this._y1 = +y}`;
	}
	bezierCurveTo(x1, y1, x2, y2, x, y) {
		this._append`C${+x1},${+y1},${+x2},${+y2},${this._x1 = +x},${this._y1 = +y}`;
	}
	arcTo(x1, y1, x2, y2, r) {
		x1 = +x1, y1 = +y1, x2 = +x2, y2 = +y2, r = +r;
		if (r < 0) throw new Error(`negative radius: ${r}`);
		let x0 = this._x1, y0 = this._y1, x21 = x2 - x1, y21 = y2 - y1, x01 = x0 - x1, y01 = y0 - y1, l01_2 = x01 * x01 + y01 * y01;
		if (this._x1 === null) this._append`M${this._x1 = x1},${this._y1 = y1}`;
		else if (!(l01_2 > epsilon$1));
		else if (!(Math.abs(y01 * x21 - y21 * x01) > epsilon$1) || !r) this._append`L${this._x1 = x1},${this._y1 = y1}`;
		else {
			let x20 = x2 - x0, y20 = y2 - y0, l21_2 = x21 * x21 + y21 * y21, l20_2 = x20 * x20 + y20 * y20, l21 = Math.sqrt(l21_2), l01 = Math.sqrt(l01_2), l = r * Math.tan((pi$1 - Math.acos((l21_2 + l01_2 - l20_2) / (2 * l21 * l01))) / 2), t01 = l / l01, t21 = l / l21;
			if (Math.abs(t01 - 1) > epsilon$1) this._append`L${x1 + t01 * x01},${y1 + t01 * y01}`;
			this._append`A${r},${r},0,0,${+(y01 * x20 > x01 * y20)},${this._x1 = x1 + t21 * x21},${this._y1 = y1 + t21 * y21}`;
		}
	}
	arc(x, y, r, a0, a1, ccw) {
		x = +x, y = +y, r = +r, ccw = !!ccw;
		if (r < 0) throw new Error(`negative radius: ${r}`);
		let dx = r * Math.cos(a0), dy = r * Math.sin(a0), x0 = x + dx, y0 = y + dy, cw = 1 ^ ccw, da = ccw ? a0 - a1 : a1 - a0;
		if (this._x1 === null) this._append`M${x0},${y0}`;
		else if (Math.abs(this._x1 - x0) > epsilon$1 || Math.abs(this._y1 - y0) > epsilon$1) this._append`L${x0},${y0}`;
		if (!r) return;
		if (da < 0) da = da % tau$1 + tau$1;
		if (da > tauEpsilon$1) this._append`A${r},${r},0,1,${cw},${x - dx},${y - dy}A${r},${r},0,1,${cw},${this._x1 = x0},${this._y1 = y0}`;
		else if (da > epsilon$1) this._append`A${r},${r},0,${+(da >= pi$1)},${cw},${this._x1 = x + r * Math.cos(a1)},${this._y1 = y + r * Math.sin(a1)}`;
	}
	rect(x, y, w, h) {
		this._append`M${this._x0 = this._x1 = +x},${this._y0 = this._y1 = +y}h${w = +w}v${+h}h${-w}Z`;
	}
	toString() {
		return this._;
	}
};
function path$1() {
	return new Path$1();
}
path$1.prototype = Path$1.prototype;
var pi = Math.PI, tau = 2 * pi, epsilon = 1e-6, tauEpsilon = tau - epsilon;
function Path() {
	this._x0 = this._y0 = this._x1 = this._y1 = null;
	this._ = "";
}
function path() {
	return new Path();
}
Path.prototype = path.prototype = {
	constructor: Path,
	moveTo: function(x, y) {
		this._ += "M" + (this._x0 = this._x1 = +x) + "," + (this._y0 = this._y1 = +y);
	},
	closePath: function() {
		if (this._x1 !== null) {
			this._x1 = this._x0, this._y1 = this._y0;
			this._ += "Z";
		}
	},
	lineTo: function(x, y) {
		this._ += "L" + (this._x1 = +x) + "," + (this._y1 = +y);
	},
	quadraticCurveTo: function(x1, y1, x, y) {
		this._ += "Q" + +x1 + "," + +y1 + "," + (this._x1 = +x) + "," + (this._y1 = +y);
	},
	bezierCurveTo: function(x1, y1, x2, y2, x, y) {
		this._ += "C" + +x1 + "," + +y1 + "," + +x2 + "," + +y2 + "," + (this._x1 = +x) + "," + (this._y1 = +y);
	},
	arcTo: function(x1, y1, x2, y2, r) {
		x1 = +x1, y1 = +y1, x2 = +x2, y2 = +y2, r = +r;
		var x0 = this._x1, y0 = this._y1, x21 = x2 - x1, y21 = y2 - y1, x01 = x0 - x1, y01 = y0 - y1, l01_2 = x01 * x01 + y01 * y01;
		if (r < 0) throw new Error("negative radius: " + r);
		if (this._x1 === null) this._ += "M" + (this._x1 = x1) + "," + (this._y1 = y1);
		else if (!(l01_2 > epsilon));
		else if (!(Math.abs(y01 * x21 - y21 * x01) > epsilon) || !r) this._ += "L" + (this._x1 = x1) + "," + (this._y1 = y1);
		else {
			var x20 = x2 - x0, y20 = y2 - y0, l21_2 = x21 * x21 + y21 * y21, l20_2 = x20 * x20 + y20 * y20, l21 = Math.sqrt(l21_2), l01 = Math.sqrt(l01_2), l = r * Math.tan((pi - Math.acos((l21_2 + l01_2 - l20_2) / (2 * l21 * l01))) / 2), t01 = l / l01, t21 = l / l21;
			if (Math.abs(t01 - 1) > epsilon) this._ += "L" + (x1 + t01 * x01) + "," + (y1 + t01 * y01);
			this._ += "A" + r + "," + r + ",0,0," + +(y01 * x20 > x01 * y20) + "," + (this._x1 = x1 + t21 * x21) + "," + (this._y1 = y1 + t21 * y21);
		}
	},
	arc: function(x, y, r, a0, a1, ccw) {
		x = +x, y = +y, r = +r, ccw = !!ccw;
		var dx = r * Math.cos(a0), dy = r * Math.sin(a0), x0 = x + dx, y0 = y + dy, cw = 1 ^ ccw, da = ccw ? a0 - a1 : a1 - a0;
		if (r < 0) throw new Error("negative radius: " + r);
		if (this._x1 === null) this._ += "M" + x0 + "," + y0;
		else if (Math.abs(this._x1 - x0) > epsilon || Math.abs(this._y1 - y0) > epsilon) this._ += "L" + x0 + "," + y0;
		if (!r) return;
		if (da < 0) da = da % tau + tau;
		if (da > tauEpsilon) this._ += "A" + r + "," + r + ",0,1," + cw + "," + (x - dx) + "," + (y - dy) + "A" + r + "," + r + ",0,1," + cw + "," + (this._x1 = x0) + "," + (this._y1 = y0);
		else if (da > epsilon) this._ += "A" + r + "," + r + ",0," + +(da >= pi) + "," + cw + "," + (this._x1 = x + r * Math.cos(a1)) + "," + (this._y1 = y + r * Math.sin(a1));
	},
	rect: function(x, y, w, h) {
		this._ += "M" + (this._x0 = this._x1 = +x) + "," + (this._y0 = this._y1 = +y) + "h" + +w + "v" + +h + "h" + -w + "Z";
	},
	toString: function() {
		return this._;
	}
};
export { Path$1 as n, path as t };
