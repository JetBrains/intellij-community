!include "TextFunc.nsh"
!include x64.nsh

!define INSTALL_OPTION_ELEMENTS 7
!define PYTHON_VERSIONS 4

${StrTok}

Function customInstallActions
  ${LineSum} "$TEMP\python.txt" $R0
  IfErrors cantOpenFile
  StrCmp $R0 ${PYTHON_VERSIONS} getPythonInfo ;info about 2 and 3 version of python
cantOpenFile:  
  MessageBox MB_OK|MB_ICONEXCLAMATION "python.txt is invalid. Python will not be downloaded."
  goto skip_python_download
getPythonInfo:  
  Call getPythonInfo
  StrCmp $0 "Error" skip_python_download
  !insertmacro INSTALLOPTIONS_READ $R2 "Desktop.ini" "Field 5" "State"
  StrCpy $R8 "$0.msi"
  StrCpy $R9 "/quiet /qn /norestart"
  StrCmp $R2 1 "" python3
  StrCpy $R2 $0
  StrCpy $R3 $1
  goto check_python
python3:  
  !insertmacro INSTALLOPTIONS_READ $R2 "Desktop.ini" "Field 6" "State"
  StrCpy $R8 "$R0.exe"
  StrCpy $R9 "InstallAllUsers=1 /quiet"
  StrCmp $R2 1 "" skip_python_download
  StrCpy $R2 $R0
  StrCpy $R3 $R1
check_python:  
  ReadRegStr $1 "HKCU" "Software\Python\PythonCore\$R2\InstallPath" ""
  StrCmp $1 "" installation_for_all_users
  goto verefy_python_launcher
installation_for_all_users:
  ReadRegStr $1 "HKLM" "Software\Python\PythonCore\$R2\InstallPath" ""
  StrCmp $1 "" get_python
verefy_python_launcher:
  IfFileExists $1python.exe python_exists get_python
get_python:
  CreateDirectory "$INSTDIR\python"
  inetc::get "$R3" "$INSTDIR\python\python_$R8"
  Pop $0
  ${If} $0 == "OK"
    ExecCmd::exec '"$INSTDIR\python\python_$R8" $R9'
  ${Else}
    MessageBox MB_OK|MB_ICONEXCLAMATION "The download is failed"
  ${EndIf}
python_exists:
skip_python_download:  
FunctionEnd

Function searchJava64
  StrCpy $0 "HKLM"
  StrCpy $1 "Software\JavaSoft\Java Development Kit\${JAVA_REQUIREMENT}"
  StrCpy $2 "JavaHome"
  SetRegView 64
  call OMReadRegStr
  SetRegView 32
  StrCpy $3 "$3\bin\java.exe"
  IfFileExists $3 done no_java_64
no_java_64:
  StrCpy $3 ""
done:
FunctionEnd

Function updatePythonControls
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R4 "Text" "Python $R2 (installed)"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R4 "Flags" "DISABLED"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R4 "Type" "checkbox"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R4 "State" "0"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R5 "Type" "checkbox"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R5 "State" "0"
FunctionEnd


Function ConfirmDesktopShortcut
  !insertmacro MUI_HEADER_TEXT "$(installation_options)" "$(installation_options_prompt)"
  ${StrRep} $0 ${PRODUCT_EXE_FILE} "64.exe" ".exe"
  ${If} $0 == ${PRODUCT_EXE_FILE}
    StrCpy $R0 "32-bit launcher"
    StrCpy $R1 "64-bit launcher"
  ${Else}
    ;there is only one launcher and it is 64-bit.
    StrCpy $R0 "64-bit launcher"
    StrCpy $R1 ""
  ${EndIf}
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 2" "Text" $R0

  ${If} $R1 != ""
    ${StrRep} $R0 ${PRODUCT_EXE_FILE_64} "64.exe" ".exe"
    ${If} $R0 == ${PRODUCT_EXE_FILE}
      call searchJava64
      ${If} $3 != ""
        !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 3" "Type" "checkbox"
        !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 3" "Text" $R1
      ${EndIf}
    ${EndIf}
  ${EndIf}
  IfFileExists "$TEMP\python.txt" deletePythonFileInfo getPythonFileInfo
deletePythonFileInfo:
  Delete "$TEMP\python.txt"
getPythonFileInfo:
  inetc::get "https://www.jetbrains.com/updates/python.txt" "$TEMP\python.txt"
  ${LineSum} "$TEMP\python.txt" $R0
  IfErrors cantOpenFile
  StrCmp $R0 ${PYTHON_VERSIONS} getPythonInfo
cantOpenFile:
  MessageBox MB_OK|MB_ICONEXCLAMATION "python.txt is not exist. Python will not be downloaded."
removePythonChoice:
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 5" "Flags" "DISABLED"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 6" "Flags" "DISABLED"
  goto association
getPythonInfo:
  Call getPythonInfo
  StrCmp $0 "Error" removePythonChoice
  ;check if pythons are already installed
  StrCpy $R2 $0
  Call searchPython
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 5" "Text" "Python $0"
  StrCmp $R2 "Absent" checkPython3
  StrCpy $R2 $0
  StrCpy $R4 "Field 5"
  StrCpy $R5 "Field 6"
  Call updatePythonControls
checkPython3:
  StrCpy $R2 $R0
  Call searchPython
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 6" "Text" "Python $R0"
  StrCmp $R2 "Absent" association
  StrCpy $R2 $R0
  StrCpy $R4 "Field 6"
  StrCpy $R5 "Field 5"
  Call updatePythonControls
association:
  StrCmp "${ASSOCIATION}" "NoAssociation" skip_association
  StrCpy $R0 ${INSTALL_OPTION_ELEMENTS}
  push "${ASSOCIATION}"
loop:
  call SplitStr
  Pop $0
  StrCmp $0 "" done
  IntOp $R0 $R0 + 1
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field $R0" "Text" "$0"
  goto loop
skip_association:
  IntOp $R0 ${INSTALL_OPTION_ELEMENTS} - 1
done:
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Settings" "NumFields" "$R0"
  !insertmacro INSTALLOPTIONS_DISPLAY "Desktop.ini"
FunctionEnd


Function getPythonInfo
  ClearErrors
  FileOpen $3 $Temp\python.txt r
  IfErrors cantOpenFile ;file can not be open.
  ${If} ${RunningX64}
    goto getPythonInfo
  ${Else}
    FileRead $3 $4
    FileRead $3 $4
  ${EndIf}
  ;get python2 info
getPythonInfo:
  FileRead $3 $4
  ${StrTok} $0 $4 " " "1" "1"
  ${StrTok} $1 $4 " " "2" "1"
  ;get python3 info
  FileRead $3 $4
  ${StrTok} $R0 $4 " " "1" "1"
  ${StrTok} $R1 $4 " " "2" "1"
  goto done
cantOpenFile:
  MessageBox MB_OK|MB_ICONEXCLAMATION "python.txt is not exist. Python will not be downloaded."
  StrCpy $0 "Error"
done:
FunctionEnd


Function searchPython
  ;$R2 - version of python
  ReadRegStr $1 "HKCU" "Software\Python\PythonCore\$R2\InstallPath" ""
  StrCmp $1 "" CU_32bit verifyPythonLauncher
CU_32bit:  
  ReadRegStr $1 "HKCU" "Software\Python\PythonCore\$R2-32\InstallPath" ""
  StrCmp $1 "" CU_64bit verifyPythonLauncher
CU_64bit:
  ReadRegStr $1 "HKCU" "Software\Python\PythonCore\$R2-64\InstallPath" ""
  StrCmp $1 "" installationForAllUsers verifyPythonLauncher

installationForAllUsers:
  ReadRegStr $1 "HKLM" "Software\Python\PythonCore\$R2\InstallPath" ""
  StrCmp $1 "" LM_32bit verifyPythonLauncher
LM_32bit:    
  ReadRegStr $1 "HKLM" "Software\Python\PythonCore\$R2-32\InstallPath" ""
  StrCmp $1 "" LM_64bit verifyPythonLauncher
LM_64bit:      
  ReadRegStr $1 "HKLM" "Software\Python\PythonCore\$R2-64\InstallPath" ""
  StrCmp $1 "" pythonAbsent
verifyPythonLauncher:
  IfFileExists $1python.exe pythonExists pythonAbsent
pythonAbsent:  
  StrCpy $R2 "Absent"
  goto done
pythonExists:  
  StrCpy $R2 "Exists" 	
done:  
FunctionEnd
