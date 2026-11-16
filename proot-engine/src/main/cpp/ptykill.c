#include <jni.h>
#include <errno.h>
#include <signal.h>
#include <sys/types.h>
#include <unistd.h>

JNIEXPORT jint JNICALL
Java_id_or_oo_pr_engine_PtyNative_nativeKill(JNIEnv *env, jclass clazz, jint pid) {
    (void) env;
    (void) clazz;
    if (pid <= 0) return -EINVAL;
    /* ptyjni calls setsid() in the child, so pid is also its process-group id. */
    if (kill(-((pid_t) pid), SIGTERM) == 0) return 0;
    int group_error = errno;
    /* The cancellation can race with setsid() in the forked child. */
    if (group_error == ESRCH && kill((pid_t) pid, SIGTERM) == 0) return 0;
    return -(group_error == ESRCH ? errno : group_error);
}
