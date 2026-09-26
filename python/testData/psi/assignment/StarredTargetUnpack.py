def returnTuple():
    return 1, 2, 3, 4

<dst1>a, *b, <dst2>c = <src>returnTuple()
<src1>((returnTuple())[0]), <src2>((returnTuple())[-1])