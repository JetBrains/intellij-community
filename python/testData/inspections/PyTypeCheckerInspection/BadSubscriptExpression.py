def test():
    x = r"""\x""
    r"""[<error descr="expression expected">\</error><error descr="Statement expected, found Py:BACKSLASH">t</error><error descr="End of statement expected">\</error><error descr="Statement expected, found Py:BACKSLASH">r</error><error descr="End of statement expected">\</error><error descr="Statement expected, found Py:BACKSLASH">v</error><error descr="End of statement expected">]</error><error descr="Statement expected, found Py:RBRACKET">"</error>""
    """
