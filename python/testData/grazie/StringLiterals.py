# TODO add f-Strings support
oneTypo = "It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human"
oneSpellcheckTypo = "It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human"
fewTypos = "It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> working for <GRAMMAR_ERROR descr="MUCH_COUNTABLE">much</GRAMMAR_ERROR> warnings"
ignoreTemplate = "It is {} friend" % fewTypos
notIgnoreOtherMistakes = "It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> friend. But I have a {1} here"

oneTypo = 'It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human'
oneSpellcheckTypo = 'It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human'
fewTypos = 'It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> working for <GRAMMAR_ERROR descr="MUCH_COUNTABLE">much</GRAMMAR_ERROR> warnings'
ignoreTemplate = 'It is {} friend' % fewTypos
notIgnoreOtherMistakes = 'It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> friend. But I have a {1} here'

print('It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human')
print('It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human')
print('It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> working for <GRAMMAR_ERROR descr="MUCH_COUNTABLE">much</GRAMMAR_ERROR> warnings')
print('It is {} friend' % fewTypos)
print('It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> friend. But I have a {1} here')
