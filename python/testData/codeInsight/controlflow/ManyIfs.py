var = 1
if a == b:
    var = 2
elif aa == bb:
    bbb = same_changet_expression

    if bbb:
        var = 3 # <--- this highlight bug (unused variable)

else:
    var = 4

return {'variable': var}