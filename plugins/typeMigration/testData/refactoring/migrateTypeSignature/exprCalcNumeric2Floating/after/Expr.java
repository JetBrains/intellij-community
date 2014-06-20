class Expr {
	public void meth(float p) {
		float vu1 = p++;
		float vu2 = p--;
		float vu3 = ++p;
		float vu4 = --p;
		float vu5 = -p;
		float vu6 = +p;

		float vb1 = p * p;
		float vb2 = p / p;
		float vb3 = p % p;
		float vb4 = p + p;
		float vb5 = p - p;
		int vb9 = p & p;
		int vba = p ^ p;
		int vbb = p | p;

		float vn1 = 0;
		vn1 *= p;
		float vn2 = 0;
		vn2 /= p;
		float vn3 = 0;
		vn3 %= p;
		float vn4 = 0;
		vn4 += p;
		float vn5 = 0;
		vn5 -= p;
		int vn6 = 0;
		vn6 <<= p;
		int vn7 = 0;
		vn7 >>= p;
		int vn8 = 0;
		vn8 >>>= p;
		int vn9 = 0;
		vn9 &= p;
		int vna = 0;
		vna ^= p;
		int vnb = 0;
		vnb |= p;
	}
}
