a = """"""
b = """
"1.0" encoding="ascii"?
"""
str = re.sub(r"""\\\\""", r"""&#92;""", str)
