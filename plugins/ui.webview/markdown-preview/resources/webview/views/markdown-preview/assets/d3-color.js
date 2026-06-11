import { c as Rgb, d as define_default, f as extend, s as Color, u as rgbConvert } from "./d3.js";
//#region node_modules/d3-color/src/math.js
var radians = Math.PI / 180;
var degrees = 180 / Math.PI;
//#endregion
//#region node_modules/d3-color/src/lab.js
var K = 18, Xn = .96422, Yn = 1, Zn = .82521, t0 = 4 / 29, t1 = 6 / 29, t2 = 3 * t1 * t1, t3 = t1 * t1 * t1;
function labConvert(o) {
	if (o instanceof Lab) return new Lab(o.l, o.a, o.b, o.opacity);
	if (o instanceof Hcl) return hcl2lab(o);
	if (!(o instanceof Rgb)) o = rgbConvert(o);
	var r = rgb2lrgb(o.r), g = rgb2lrgb(o.g), b = rgb2lrgb(o.b), y = xyz2lab((.2225045 * r + .7168786 * g + .0606169 * b) / Yn), x, z;
	if (r === g && g === b) x = z = y;
	else {
		x = xyz2lab((.4360747 * r + .3850649 * g + .1430804 * b) / Xn);
		z = xyz2lab((.0139322 * r + .0971045 * g + .7141733 * b) / Zn);
	}
	return new Lab(116 * y - 16, 500 * (x - y), 200 * (y - z), o.opacity);
}
function lab(l, a, b, opacity) {
	return arguments.length === 1 ? labConvert(l) : new Lab(l, a, b, opacity == null ? 1 : opacity);
}
function Lab(l, a, b, opacity) {
	this.l = +l;
	this.a = +a;
	this.b = +b;
	this.opacity = +opacity;
}
define_default(Lab, lab, extend(Color, {
	brighter(k) {
		return new Lab(this.l + K * (k == null ? 1 : k), this.a, this.b, this.opacity);
	},
	darker(k) {
		return new Lab(this.l - K * (k == null ? 1 : k), this.a, this.b, this.opacity);
	},
	rgb() {
		var y = (this.l + 16) / 116, x = isNaN(this.a) ? y : y + this.a / 500, z = isNaN(this.b) ? y : y - this.b / 200;
		x = Xn * lab2xyz(x);
		y = Yn * lab2xyz(y);
		z = Zn * lab2xyz(z);
		return new Rgb(lrgb2rgb(3.1338561 * x - 1.6168667 * y - .4906146 * z), lrgb2rgb(-.9787684 * x + 1.9161415 * y + .033454 * z), lrgb2rgb(.0719453 * x - .2289914 * y + 1.4052427 * z), this.opacity);
	}
}));
function xyz2lab(t) {
	return t > t3 ? Math.pow(t, 1 / 3) : t / t2 + t0;
}
function lab2xyz(t) {
	return t > t1 ? t * t * t : t2 * (t - t0);
}
function lrgb2rgb(x) {
	return 255 * (x <= .0031308 ? 12.92 * x : 1.055 * Math.pow(x, 1 / 2.4) - .055);
}
function rgb2lrgb(x) {
	return (x /= 255) <= .04045 ? x / 12.92 : Math.pow((x + .055) / 1.055, 2.4);
}
function hclConvert(o) {
	if (o instanceof Hcl) return new Hcl(o.h, o.c, o.l, o.opacity);
	if (!(o instanceof Lab)) o = labConvert(o);
	if (o.a === 0 && o.b === 0) return new Hcl(NaN, 0 < o.l && o.l < 100 ? 0 : NaN, o.l, o.opacity);
	var h = Math.atan2(o.b, o.a) * degrees;
	return new Hcl(h < 0 ? h + 360 : h, Math.sqrt(o.a * o.a + o.b * o.b), o.l, o.opacity);
}
function hcl(h, c, l, opacity) {
	return arguments.length === 1 ? hclConvert(h) : new Hcl(h, c, l, opacity == null ? 1 : opacity);
}
function Hcl(h, c, l, opacity) {
	this.h = +h;
	this.c = +c;
	this.l = +l;
	this.opacity = +opacity;
}
function hcl2lab(o) {
	if (isNaN(o.h)) return new Lab(o.l, 0, 0, o.opacity);
	var h = o.h * radians;
	return new Lab(o.l, Math.cos(h) * o.c, Math.sin(h) * o.c, o.opacity);
}
define_default(Hcl, hcl, extend(Color, {
	brighter(k) {
		return new Hcl(this.h, this.c, this.l + K * (k == null ? 1 : k), this.opacity);
	},
	darker(k) {
		return new Hcl(this.h, this.c, this.l - K * (k == null ? 1 : k), this.opacity);
	},
	rgb() {
		return hcl2lab(this).rgb();
	}
}));
//#endregion
export { hcl as t };
