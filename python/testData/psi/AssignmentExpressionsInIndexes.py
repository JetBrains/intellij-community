s = [1, 2]

s[(c := 0)]  # valid
s[d := 0]   # valid

s[(d := 0): (e := 1)]   # valid
s[d := 0: (e := 1)]   # invalid
s[d := 0: e := 1]   # invalid