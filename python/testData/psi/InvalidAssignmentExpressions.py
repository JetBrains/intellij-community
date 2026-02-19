# Parenthesis everywhere because
# unparenthesized assignment expressions are prohibited
# at the top level of an expression statement

(x := y := z := 0)

(a[i] := x)
(self.rest := [])

(p: Optional[int] := None)

(b := -)
(x := )

x = (b[j] := z) = 'spam'  # z is a reference

[x1 for x1 := 2 in (1, 2, 3)]
[x1 for (x1 := 2) in (1, 2, 3)]