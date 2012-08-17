def dN(x,mu,si):    # pdf normal distribution
    z = (x - mu) / si
    return exp(-0.5 * z ** 2) / sqrt(2 * pi * si ** 2)
S0 = 100.0       # initial index level