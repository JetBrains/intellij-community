# sign option is available for numeric only
"{:+}".format(<warning descr="Unexpected type str">"s"</warning>)
"{:+}".format(1)
"{:+}".format(1.0)
"{:+}".format(complex(2, 3))

# alternate form option is available for numeric only
"{:#}".format(<warning descr="Unexpected type str">"s"</warning>)
"{:#}".format(1)
"{:#}".format(1.0)
"{:#}".format(complex(2, 3))

# thousand separator is available for numeric only 
"{:,}".format(<warning descr="Unexpected type str">"s"</warning>)
"{:,}".format(1)
"{:,}".format(1.0)
"{:,}".format(complex(2, 3))

# zero padding is available for numeric only
"{:0}".format(<warning descr="Unexpected type str">"s"</warning>)
"{:0}".format(1)
"{:0}".format(1.0)
"{:0}".format(complex(2, 3))

# precision is available for str, float, and complex
"{:.2}".format("s")
"{:.2}".format(1.0)
"{:.2}".format(complex(2, 3))

# presentation types for integer
"{:b}".format(<warning descr="Unexpected type str">"s"</warning>)
"{:c}".format(<warning descr="Unexpected type str">"s"</warning>)
"{:d}".format(<warning descr="Unexpected type str">"s"</warning>)
"{:o}".format(<warning descr="Unexpected type str">"s"</warning>)
"{:x}".format(<warning descr="Unexpected type str">"s"</warning>)
"{:X}".format(<warning descr="Unexpected type str">"s"</warning>)
"{:n}".format(<warning descr="Unexpected type str">"s"</warning>)

"{:b}".format(1)
"{:c}".format(1)
"{:d}".format(1)
"{:o}".format(1)
"{:x}".format(1)
"{:X}".format(1)
"{:n}".format(1)

"{:b}".format(True)
"{:c}".format(True)
"{:d}".format(True)
"{:o}".format(True)
"{:x}".format(True)
"{:X}".format(True)
"{:n}".format(True)

"{:b}".format(<warning descr="Unexpected type float">1.0</warning>)
"{:c}".format(<warning descr="Unexpected type float">1.0</warning>)
"{:d}".format(<warning descr="Unexpected type float">1.0</warning>)
"{:o}".format(<warning descr="Unexpected type float">1.0</warning>)
"{:x}".format(<warning descr="Unexpected type float">1.0</warning>)
"{:X}".format(<warning descr="Unexpected type float">1.0</warning>)
"{:n}".format(1.0)

"{:b}".format(<warning descr="Unexpected type complex">complex(2, 3)</warning>)
"{:c}".format(<warning descr="Unexpected type complex">complex(2, 3)</warning>)
"{:d}".format(<warning descr="Unexpected type complex">complex(2, 3)</warning>)
"{:o}".format(<warning descr="Unexpected type complex">complex(2, 3)</warning>)
"{:x}".format(<warning descr="Unexpected type complex">complex(2, 3)</warning>)
"{:X}".format(<warning descr="Unexpected type complex">complex(2, 3)</warning>)
"{:n}".format(complex(2, 3))

# presentation types for floating and decimal values
"{:e}".format(1)
"{:E}".format(1)
"{:f}".format(1)
"{:F}".format(1)
"{:g}".format(1)
"{:G}".format(1)
"{:%}".format(1)

"{:e}".format(True)
"{:E}".format(True)
"{:f}".format(True)
"{:F}".format(True)
"{:g}".format(True)
"{:G}".format(True)
"{:%}".format(True)

"{:e}".format(1.0)
"{:E}".format(1.0)
"{:f}".format(1.0)
"{:F}".format(1.0)
"{:g}".format(1.0)
"{:G}".format(1.0)
"{:%}".format(1.0)

"{:e}".format(complex(2, 3))
"{:E}".format(complex(2, 3))
"{:f}".format(complex(2, 3))
"{:F}".format(complex(2, 3))
"{:g}".format(complex(2, 3))
"{:G}".format(complex(2, 3))
"{:%}".format(<warning descr="Unexpected type complex">complex(2, 3)</warning>)

"{:e}".format(<warning descr="Unexpected type str">"s"</warning>)
"{:E}".format(<warning descr="Unexpected type str">"s"</warning>)
"{:f}".format(<warning descr="Unexpected type str">"s"</warning>)
"{:F}".format(<warning descr="Unexpected type str">"s"</warning>)
"{:g}".format(<warning descr="Unexpected type str">"s"</warning>)
"{:G}".format(<warning descr="Unexpected type str">"s"</warning>)
"{:%}".format(<warning descr="Unexpected type str">"s"</warning>)

# types combinations
<warning descr="The format options in chunk \"0\" are incompatible">"{:,s}"</warning>.format(1)

# refrefence
a = dict(a=1, b=1)
"{:s}".format(a)
"{:s}".format(None)
"{}"