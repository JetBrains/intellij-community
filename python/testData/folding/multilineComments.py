<fold text='...'># the first line in comment
#    the second line
# the third line</fold>
print("")
<fold text='...'># multiline comment starts here
#    line in comment
# line in comment
#    multiline comment ends here</fold>
print("")

<fold text='...'># one more comment
#
# one more comment
#
#</fold>


def normal_foldable_element_to_ensure_two_step_folding() :<fold text='...'>
    pass</fold>
