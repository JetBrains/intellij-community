"==[ DATA TYPES ]===============================================================

let string_1 = "C:\\WinNT"
let string_2 = 'C:\WinNT'
let funcref1 = function ("MyFunc")
let List_1   = [
	\ "a",
	\ "b",
	\ "c"]
let List2 = split ("a b c")
let Dictionary = {
	\ 1: 'one',
	\ 2: 'two',
	\ 3: 'three'}

let myDict = {
	\'data': [0, 1, 2, 3]}

function myDict.len () dict
	return len (self.data)
endfunction myDict.len

runtime setup.vim
source ~/vimfiles/setup.vim

0xFF
100.1
15.45e-2
2 * 2.0

:let  l:number = 1
:let &l:number = 1
:let @a = "hello!"
