
#if defined(WIN32)
#include <windows.h>
#else
#include <jni.h>
#include <signal.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

int isKernel26OrHigher();
#endif

JNIEXPORT void JNICALL Java_com_intellij_rt_execution_application_AppMain_triggerControlBreak
  (JNIEnv *env, jclass clazz) {
#if defined(WIN32)
  GenerateConsoleCtrlEvent(CTRL_BREAK_EVENT, 0);
#else
  if (isKernel26OrHigher()) {
    kill (getpid(), SIGQUIT);
  } else {
    int ppid = getppid();
    char buffer[1024];
    sprintf(buffer, "/proc/%d/status", ppid);
    FILE * fp;
    if ( (fp = fopen(buffer, "r")) != NULL )
    {
      char s[124];
      char * ppid_name = "PPid:";
      while (fscanf (fp, "%s\n", s) > 0) {
        if (strcmp(s, ppid_name) == 0) {
          int pppid;
          fscanf(fp, "%d", &pppid);
          kill (pppid, SIGQUIT);
          break;
        }
      }

      fclose (fp);
    }
  }
#endif
}

#ifndef WIN32

int isKernel26OrHigher() {
  char buffer[1024];
  FILE * fp;
  if ( (fp = fopen("/proc/version", "r")) != NULL )
  {
     int major;
     int minor;
     fscanf(fp, "Linux version %d.%d", &major, &minor);
     if (major < 2) return 0;
     if (major == 2) return minor >= 6;
     fclose (fp);
     return 1;  
  }

  return 0;
}
#endif