n = 8
a = "{n:d} bottles of {what:s} on the {where:s}" \
    """
    {n:d} bottles
    of {what:s}
    """ \
    r'\n/ take {howmuch!r:s} down \n/' \
    ur"pass it {how:>8s}" \
    "{new_n:#d} {{that is, {percent:+3.2f}% less}} " \
    "bottles of {what:>6s} on the {where:s}" \
    .format(n=n, where="wall", howmuch=u'one', how="'round\t", new_n=(n - 1), percent=100 * (1 - ((n - 1.0) / n)),
            what="beer")
print a
