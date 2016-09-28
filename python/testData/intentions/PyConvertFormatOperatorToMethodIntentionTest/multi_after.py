n = 8
a = "{n:d} bottles of {what} on the {where}" \
    """
    {n:d} bottles
    of {what}
    """ \
    r'\n/ take {howmuch!r} down \n/' \
    ur"pass it {how:>8}" \
    "{new_n:#d} {{that is, {percent:+3.2f}% less}} " \
    "bottles of {what:>6} on the {where}" \
    .format(n=n, where="wall", howmuch=u'one', how="'round\t", new_n=(n - 1), percent=100 * (1 - ((n - 1.0) / n)),
            what="beer")
print a
