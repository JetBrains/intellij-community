!include "TextFunc.nsh"
!define INSTALL_OPTION_ELEMENTS 8
!define PYTHON_VERSIONS 4

${StrTok}

Function customPreInstallActions
  IfFileExists "$TEMP\python.txt" deletePythonFileInfo getPythonFileInfo
deletePythonFileInfo:
  Delete "$TEMP\python.txt"
getPythonFileInfo:
  inetc::get "https://www.jetbrains.com/updates/python.txt" "$TEMP\python.txt"
  ${LineSum} "$TEMP\python.txt" $R0
  IfErrors cantOpenFile
  StrCmp $R0 ${PYTHON_VERSIONS} getPythonInfo
cantOpenFile:
  MessageBox MB_OK|MB_ICONEXCLAMATION "python.txt is not exist. Python will not be downloaded." /SD IDOK
removePythonChoice:
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 6" "Flags" "DISABLED"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 7" "Flags" "DISABLED"
  goto done
getPythonInfo:
  Call getPythonInfo
  StrCmp $0 "Error" removePythonChoice
; check if pythons are already installed
  StrCpy $R2 $0
  Call searchPython
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 6" "Text" "Python $R2"
  StrCmp $4 "Absent" checkPython3
  StrCpy $R4 "Field 6"
  StrCpy $R5 "Field 7"
  Call updatePythonControls
checkPython3:
  StrCpy $R2 $R0
  Call searchPython
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" "Field 7" "Text" "Python $R2"
  StrCmp $4 "Absent" done
  StrCpy $R4 "Field 7"
  StrCpy $R5 "Field 6"
  Call updatePythonControls
done:  
FunctionEnd

Function customInstallActions
  ${LineSum} "$TEMP\python.txt" $R0
  IfErrors cantOpenFile
; info about 2 and 3 version of python
  StrCmp $R0 ${PYTHON_VERSIONS} getPythonInfo
cantOpenFile:
  MessageBox MB_OK|MB_ICONEXCLAMATION "python.txt is invalid. Python will not be downloaded." /SD IDOK
  goto skip_python_download
getPythonInfo:  
  Call getPythonInfo
  StrCmp $0 "Error" skip_python_download
  !insertmacro INSTALLOPTIONS_READ $R2 "Desktop.ini" "Field 6" "State"
  StrCpy $R7 "msiexec.exe /i "
  StrCpy $R8 "$0.msi"
  StrCpy $R9 "/quiet /qn"
  StrCmp $R2 1 "" python3
  StrCpy $R2 $0
  StrCpy $R3 $1
  goto check_python
python3:  
  !insertmacro INSTALLOPTIONS_READ $R2 "Desktop.ini" "Field 7" "State"
  StrCpy $R7 ""
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
  inetc::get "$R3" "$INSTDIR\python\python_$R8" /END
  Pop $0
  ${If} $0 == "OK"
    ExecDos::exec '$R7"$INSTDIR\python\python_$R8" $R9'
  ${Else}
    MessageBox MB_OK|MB_ICONEXCLAMATION "The download is failed: $0" /SD IDOK
  ${EndIf}
python_exists:
skip_python_download:  
FunctionEnd

Function customPostInstallActions
  DetailPrint "There are no custom post-install actions."
FunctionEnd

Function un.customUninstallActions
  DetailPrint "There are no custom uninstall actions."
FunctionEnd

Function updatePythonControls
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R4 "Text" "Python $R2 (installed)"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R4 "Flags" "DISABLED"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R4 "Type" "checkbox"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R4 "State" "0"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R5 "Type" "checkbox"
  !insertmacro INSTALLOPTIONS_WRITE "Desktop.ini" $R5 "State" "0"
FunctionEnd

Function getPythonInfo
  ClearErrors
  FileOpen $3 $Temp\python.txt r
; file can not be open.
  IfErrors cantOpenFile
  ${If} ${RunningX64}
    goto getPythonInfo
  ${Else}
    FileRead $3 $4
    FileRead $3 $4
  ${EndIf}
; get python2 info
getPythonInfo:
  FileRead $3 $4
  ${StrTok} $0 $4 " " "1" "1"
  ${StrTok} $1 $4 " " "2" "1"
; get python3 info
  FileRead $3 $4
  ${StrTok} $R0 $4 " " "1" "1"
  ${StrTok} $R1 $4 " " "2" "1"
  goto done
cantOpenFile:
  MessageBox MB_OK|MB_ICONEXCLAMATION "python.txt is not exist. Python will not be downloaded." /SD IDOK
  StrCpy $0 "Error"
done:
FunctionEnd

Function searchPython
; $R2 - version of python
  StrCpy $0 "HKCU"
  StrCpy $1 "Software\Python\PythonCore\$R2\InstallPath"
  StrCpy $2 ""
  SetRegView 64
  call OMReadRegStr
  SetRegView 32
  StrCmp $3 "" CU_32bit verifyPythonLauncher

CU_32bit:
  call OMReadRegStr
  StrCmp $3 "" installationForAllUsers verifyPythonLauncher

installationForAllUsers:
  StrCpy $0 "HKLM"
  call OMReadRegStr
  SetRegView 64
  call OMReadRegStr
  SetRegView 32
  StrCmp $3 "" LM_32bit verifyPythonLauncher

LM_32bit:
  call OMReadRegStr
  StrCmp $3 "" pythonAbsent
verifyPythonLauncher:
  IfFileExists $3python.exe pythonExists pythonAbsent
pythonAbsent:  
  StrCpy $4 "Absent"
  goto done
pythonExists:  
  StrCpy $4 "Exists"
done:  
FunctionEnd
