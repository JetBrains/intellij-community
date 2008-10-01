// fileWatcher3.cpp : Defines the entry point for the console application.
//

#include "stdafx.h"

struct WatchRootInfo {
	char driveLetter;
	HANDLE hThread;
	HANDLE hStopEvent;
	bool bInitialized;
	bool bUsed;
	bool bFailed;
};

const int ROOT_COUNT = 26;

WatchRootInfo watchRootInfos[ROOT_COUNT];

CRITICAL_SECTION csOutput;

bool IsNetworkDrive(const char *name) 
{
    const int BUF_SIZE = 1024;
    char buffer[BUF_SIZE];
    UNIVERSAL_NAME_INFO* uni = (UNIVERSAL_NAME_INFO*) buffer;
    DWORD size = BUF_SIZE;

    DWORD result = WNetGetUniversalNameA(
        name,  // path for network resource
        UNIVERSAL_NAME_INFO_LEVEL, // level of information
        buffer, // name buffer
        &size // size of buffer
    );

    return result == NO_ERROR;
}

bool IsSubstedDrive(const char* name) 
{
    char deviceName[3] = {name[0], name[1], 0};
    const int BUF_SIZE = 1024;
    char targetPath[BUF_SIZE];

    DWORD result = QueryDosDeviceA(deviceName, targetPath, BUF_SIZE);
    if (result == 0) {
        return false;
    }
    else {
        bool result = (targetPath[0] == '\\' && targetPath[1] == '?' && targetPath[2] == '?');
        return result;
    }
}

bool IsUnwatchableFS(const char *path)
{
    char volumeName[MAX_PATH];
	char fsName[MAX_PATH];
	DWORD fsFlags;
	DWORD maxComponentLength;
	SetErrorMode(SEM_FAILCRITICALERRORS);
	if (!GetVolumeInformationA(path, volumeName, MAX_PATH-1, NULL, &maxComponentLength, &fsFlags, fsName, MAX_PATH-1))
		return false;
	if (strcmp(fsName, "NTFS") && strcmp(fsName, "FAT") && strcmp(fsName, "FAT32"))
		return true;

    if (!strcmp(fsName, "NTFS") && maxComponentLength != 255 && !(fsFlags & FILE_SUPPORTS_REPARSE_POINTS))
	{
		// SAMBA reports itself as NTFS
		return true;
	}

	return false;
}

bool IsWatchable(const char *path)
{
	if (IsNetworkDrive(path))
		return false;
	if (IsSubstedDrive(path))
		return false;
	if (IsUnwatchableFS(path))
		return false;
	return true;
}

const int BUFSIZE = 1024;

void PrintMountPointsForVolume(HANDLE hVol, const char* volumePath, char *Buf)
{
    HANDLE hPt;                  // handle for mount point scan
    char Path[BUFSIZE];          // string buffer for mount points
    DWORD dwSysFlags;            // flags that describe the file system
    char FileSysNameBuf[BUFSIZE];

    // Is this volume NTFS? 
    GetVolumeInformationA(Buf, NULL, 0, NULL, NULL, &dwSysFlags, FileSysNameBuf, BUFSIZE);

    // Detect support for reparse points, and therefore for volume 
    // mount points, which are implemented using reparse points. 

    if (! (dwSysFlags & FILE_SUPPORTS_REPARSE_POINTS)) {
       return;
    } 

    // Start processing mount points on this volume. 
    hPt = FindFirstVolumeMountPointA(
        Buf, // root path of volume to be scanned
        Path, // pointer to output string
        BUFSIZE // size of output buffer
    );

    // Shall we error out?
    if (hPt == INVALID_HANDLE_VALUE) {
        return;
    } 

    // Process the volume mount point.
    do {
		printf("%s%s\n", volumePath, Path);
    } while (FindNextVolumeMountPointA(hPt, Path, BUFSIZE));

    FindVolumeMountPointClose(hPt);
}

void PrintMountPoints(const char *path)
{
	char volumeUniqueName[128];
	BOOL res = GetVolumeNameForVolumeMountPointA(path, volumeUniqueName, 128);
	if (!res) {
        return;
    }
   
    char buf[BUFSIZE];            // buffer for unique volume identifiers
    HANDLE hVol;                  // handle for the volume scan

    // Open a scan for volumes.
    hVol = FindFirstVolumeA(buf, BUFSIZE );

    // Shall we error out?
    if (hVol == INVALID_HANDLE_VALUE) {
        return;
    }

    do {
       if (!strcmp(buf, volumeUniqueName)) {
		   PrintMountPointsForVolume(hVol, path, buf);
	   }
    } while (FindNextVolumeA(hVol, buf, BUFSIZE));

    FindVolumeClose(hVol);
}

void PrintChangeInfo(char *rootPath, FILE_NOTIFY_INFORMATION *info)
{
	char FileNameBuffer[_MAX_PATH];
	int converted = WideCharToMultiByte(CP_ACP, 0, info->FileName, info->FileNameLength/sizeof(WCHAR), FileNameBuffer, _MAX_PATH-1, NULL, NULL);
	FileNameBuffer[converted] = '\0';
	char *command;
	if (info->Action == FILE_ACTION_ADDED || info->Action == FILE_ACTION_RENAMED_OLD_NAME)
	{
		command = "CREATE";
	}
	else if (info->Action == FILE_ACTION_REMOVED || info->Action == FILE_ACTION_RENAMED_OLD_NAME)
	{
		command = "DELETE";
	}
	else if (info->Action == FILE_ACTION_MODIFIED)
	{
		command = "CHANGE";	
	}
	else
	{
		return;  // unknown command
	}

	EnterCriticalSection(&csOutput);
	puts(command);
	printf("%s", rootPath);
	puts(FileNameBuffer);
	fflush(stdout);
	LeaveCriticalSection(&csOutput);
}

DWORD WINAPI WatcherThread(void *param)
{
	WatchRootInfo *info = (WatchRootInfo *) param;

	OVERLAPPED overlapped;
	memset(&overlapped, 0, sizeof(overlapped));
	overlapped.hEvent = CreateEvent(NULL, FALSE, FALSE, NULL);

	char rootPath[8];
	sprintf_s(rootPath, 8, "%c:\\", info->driveLetter);
	HANDLE hRootDir = CreateFileA(rootPath, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
		NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OVERLAPPED, NULL);

	int buffer_size = 10240;
	char *buffer = new char[buffer_size];

	HANDLE handles [2];
	handles [0] = info->hStopEvent;
	handles [1] = overlapped.hEvent;
	while(true)
	{
		int rcDir = ReadDirectoryChangesW(hRootDir, buffer, buffer_size, TRUE, 
			FILE_NOTIFY_CHANGE_FILE_NAME |
			FILE_NOTIFY_CHANGE_DIR_NAME | 
			FILE_NOTIFY_CHANGE_ATTRIBUTES | 
			FILE_NOTIFY_CHANGE_SIZE |
			FILE_NOTIFY_CHANGE_LAST_WRITE,
			NULL,
			&overlapped, 
			NULL);
		if (rcDir == 0)
		{
			info->bFailed = true;
			break;
		}

		int rc = WaitForMultipleObjects(2, handles, FALSE, INFINITE);
		if (rc == WAIT_OBJECT_0)
		{
			break;
		}
		if (rc == WAIT_OBJECT_0+1)
		{
			FILE_NOTIFY_INFORMATION *info = (FILE_NOTIFY_INFORMATION *) buffer;
			while(true) 
			{
				PrintChangeInfo(rootPath, info);
				if (!info->NextEntryOffset)
					break;
				info = (FILE_NOTIFY_INFORMATION *) ((char *) info + info->NextEntryOffset);
			}
		}
	}
	CloseHandle(overlapped.hEvent);
	CloseHandle(hRootDir);
	delete[] buffer;
	return 0;
}

void MarkAllRootsUnused()
{
	for(int i=0; i<ROOT_COUNT; i++)
	{
		watchRootInfos [i].bUsed = false;
	}
}

void StartRoot(WatchRootInfo *info)
{
	info->hStopEvent = CreateEvent(NULL, FALSE, FALSE, NULL);
	info->hThread = CreateThread(NULL, 0, &WatcherThread, info, 0, NULL);
	info->bInitialized = true;
}

void StopRoot(WatchRootInfo *info)
{
	SetEvent(info->hStopEvent);
	WaitForSingleObject(info->hThread, INFINITE);
	CloseHandle(info->hThread);
	CloseHandle(info->hStopEvent);
	info->bInitialized = false;
}

void UpdateRoots()
{
	bool haveUsedRoots = false;
	for(int i=0; i<ROOT_COUNT; i++)
	{
		if (watchRootInfos [i].bInitialized && (!watchRootInfos [i].bUsed || watchRootInfos [i].bFailed))
		{
			StopRoot(&watchRootInfos [i]);
			watchRootInfos [i].bFailed = false;
		}
		if (watchRootInfos [i].bUsed)
		{
			if (!haveUsedRoots)
			{
				haveUsedRoots = true;
				EnterCriticalSection(&csOutput);
				puts("UNWATCHEABLE");
			}

			char rootPath[8];
			sprintf_s(rootPath, 8, "%c:\\", watchRootInfos [i].driveLetter);
			if (!IsWatchable(rootPath))
			{
				puts(rootPath);
				continue;
			}
			if (!watchRootInfos [i].bInitialized)
			{
				StartRoot(&watchRootInfos [i]);
			}
			PrintMountPoints(rootPath);
		}
	}
	if (haveUsedRoots)
	{
		puts("#");
		fflush(stdout);
		LeaveCriticalSection(&csOutput);
	}
}

int _tmain(int argc, _TCHAR* argv[])
{
	InitializeCriticalSection(&csOutput);

	for(int i=0; i<26; i++)
	{
		watchRootInfos [i].driveLetter = 'A' + i;
		watchRootInfos [i].bInitialized = false;
		watchRootInfos [i].bUsed = false;
	}

	char buffer[256];
	while(true)
	{
		if (!gets_s(buffer, sizeof(buffer)-1))
			break;

		if (!strcmp(buffer, "ROOTS"))
		{
			MarkAllRootsUnused();
			bool failed = false;
			while(true)
			{
				if (!gets_s(buffer, sizeof(buffer)-1))
				{
					failed = true;
					break;
				}
				if (buffer [0] == '#')
					break;
				int driveLetterPos = 0;
				_strupr_s(buffer, sizeof(buffer)-1);
				char driveLetter = buffer[0];
				if (driveLetter == '|')
					driveLetter = buffer[1];
				if (driveLetter >= 'A' && driveLetter <= 'Z')
				{
					watchRootInfos [driveLetter-'A'].bUsed = true;
				}
			}
			if (failed)
				break;

			UpdateRoots();
		}
		if (!strcmp(buffer, "EXIT"))
			break;
	}

	MarkAllRootsUnused();
	UpdateRoots();
	
	DeleteCriticalSection(&csOutput);
}
