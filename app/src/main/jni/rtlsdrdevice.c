/*
 * rtl_tcp_andro is a library that uses libusb and librtlsdr to
 * turn your Realtek RTL2832 based DVB dongle into a SDR receiver.
 * It independently implements the rtl-tcp API protocol for native Android usage.
 * Copyright (C) 2016 by Martin Marinov <martintzvetomirov@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#include <jni.h>
#include <unistd.h>
#include <string.h>
#include <stdlib.h>
#include "common.h"
#include "rtl-sdr-android.h"
#include "sdrtcp.h"
#include "SdrException.h"
#include "tcp_commands.h"

#define RUN_OR(command, exit_command) { \
    int cmd_result = command; \
    if (cmd_result != 0) { \
        throwExceptionWithInt(env, "com/sdrtouch/core/exceptions/SdrException", cmd_result); \
        exit_command; \
    }; \
}

#define RUN_OR_GOTO(command, label) RUN_OR(command, goto label);
static volatile int do_exit = 0;


typedef struct rtlsdr_android {
    sdrtcp_t tcpserv;
    rtlsdr_dev_t * rtl_dev;
} rtlsdr_android_t;

#define WITH_DEV(x) rtlsdr_android_t* x = (rtlsdr_android_t*) pointer

void initialize(JNIEnv *env) {
    LOGI_NATIVE("Initializing");
}

static int set_gain_by_index(rtlsdr_dev_t *_dev, unsigned int index)
{
    int res = 0;
    int* gains;
    int count = rtlsdr_get_tuner_gains(_dev, NULL);

    if (count > 0 && (unsigned int)count > index) {
        gains = malloc(sizeof(int) * count);
        rtlsdr_get_tuner_gains(_dev, gains);

        res = rtlsdr_set_tuner_gain(_dev, gains[index]);

        free(gains);
    }

    return res;
}

static int set_gain_by_perc(rtlsdr_dev_t *_dev, unsigned int percent)
{
    int res = 0;
    int* gains;
    int count = rtlsdr_get_tuner_gains(_dev, NULL);
    unsigned int index = (percent * count) / 100;
    if (index < 0) index = 0;
    if (index >= (unsigned int) count) index = (unsigned int) (count - 1);

    gains = malloc(sizeof(int) * count);
    rtlsdr_get_tuner_gains(_dev, gains);

    res = rtlsdr_set_tuner_gain(_dev, gains[index]);

    free(gains);

    return res;
}

static jint SUPPORTED_COMMANDS[] = {
        TCP_SET_FREQ,
        TCP_SET_SAMPLE_RATE,
        TCP_SET_GAIN_MODE,
        TCP_SET_GAIN,
        TCP_SET_FREQ_CORRECTION,
        TCP_SET_IF_TUNER_GAIN,
        TCP_SET_TEST_MODE,
        TCP_SET_AGC_MODE,
        TCP_SET_DIRECT_SAMPLING,
        TCP_SET_OFFSET_TUNING,
        TCP_SET_RTL_XTAL,
        TCP_SET_TUNER_XTAL,
        TCP_SET_TUNER_GAIN_BY_ID,
        TCP_ANDROID_EXIT,
        TCP_ANDROID_GAIN_BY_PERCENTAGE
};

void tcpCommandCallback(sdrtcp_t * tcpserv, void * pointer, sdr_tcp_command_t * cmd) {
    WITH_DEV(dev);
    if (dev->rtl_dev == NULL) return;

    switch(cmd->command) {
        case TCP_SET_FREQ:
            rtlsdr_set_center_freq(dev->rtl_dev,cmd->parameter);
            break;
        case TCP_SET_SAMPLE_RATE:
            LOGI("set sample rate %ld", cmd->parameter);
            rtlsdr_set_sample_rate(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_SET_GAIN_MODE:
            LOGI("set gain mode %ld", cmd->parameter);
            rtlsdr_set_tuner_gain_mode(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_SET_GAIN:
            LOGI("set gain %ld", cmd->parameter);
            rtlsdr_set_tuner_gain(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_SET_FREQ_CORRECTION:
            LOGI("set freq correction %ld", cmd->parameter);
            rtlsdr_set_freq_correction(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_SET_IF_TUNER_GAIN:
            rtlsdr_set_tuner_if_gain(dev->rtl_dev, cmd->parameter >> 16, (short)(cmd->parameter & 0xffff));
            break;
        case TCP_SET_TEST_MODE:
            LOGI("set test mode %ld", cmd->parameter);
            rtlsdr_set_testmode(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_SET_AGC_MODE:
            LOGI("set agc mode %ld", cmd->parameter);
            rtlsdr_set_agc_mode(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_SET_DIRECT_SAMPLING:
            LOGI("set direct sampling %ld", cmd->parameter);
            rtlsdr_set_direct_sampling(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_SET_OFFSET_TUNING:
            LOGI("set offset tuning %ld", cmd->parameter);
            rtlsdr_set_offset_tuning(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_SET_RTL_XTAL:
            LOGI("set rtl xtal %d", cmd->parameter);
            rtlsdr_set_xtal_freq(dev->rtl_dev, cmd->parameter, 0);
            break;
        case TCP_SET_TUNER_XTAL:
            LOGI("set tuner xtal %dl", cmd->parameter);
            rtlsdr_set_xtal_freq(dev->rtl_dev, 0, cmd->parameter);
            break;
        case TCP_SET_TUNER_GAIN_BY_ID:
            LOGI("set tuner gain by index %d", cmd->parameter);
            set_gain_by_index(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_ANDROID_EXIT:
            LOGI("tcpCommandCallback: client requested to close rtl_tcp_andro");
            sdrtcp_stop_serving_client(tcpserv);
            break;
        case TCP_ANDROID_GAIN_BY_PERCENTAGE:
            set_gain_by_perc(dev->rtl_dev, cmd->parameter);
            break;
        default:
            // don't forget to add any new commands into SUPPORTED_COMMANDS!
            break;
    }
}

void tcpClosedCallback(sdrtcp_t * tcpserv, void * pointer) {
    WITH_DEV(dev);
    if (dev->rtl_dev == NULL) return;
    rtlsdr_cancel_async(dev->rtl_dev);
}

void rtlsdr_callback(unsigned char *buf, uint32_t len, void *pointer) {
    WITH_DEV(dev);
    if (dev->rtl_dev == NULL) return;
    sdrtcp_feed(&dev->tcpserv, buf, len / 2);
}

JNIEXPORT jboolean JNICALL
Java_com_sdrtouch_rtlsdr_driver_RtlSdrDevice_openAsync__JIIJJIILjava_lang_String_2Ljava_lang_String_2(
        JNIEnv *env, jobject instance, jlong pointer, jint fd, jint gain, jlong samplingrate,
        jlong frequency, jint port, jint ppm, jstring address_, jstring devicePath_) {
    WITH_DEV(dev);
    const char *devicePath = (*env)->GetStringUTFChars(env, devicePath_, 0);
    const char *address = (*env)->GetStringUTFChars(env, address_, 0);

    EXCEPT_SAFE_NUM(jclass clazz = (*env)->GetObjectClass(env, instance));
    EXCEPT_SAFE_NUM(jmethodID announceOnOpen = (*env)->GetMethodID(env, clazz, "announceOnOpen", "()V"));

    rtlsdr_dev_t * device = NULL;
    RUN_OR_GOTO(rtlsdr_open2(&device, fd, devicePath), rel_jni);

    if (ppm != 0) {
        if (rtlsdr_set_freq_correction(device, ppm) < 0) {
            LOGI("WARNING: Failed to set ppm to %d", ppm);
        }
    }

    int result = 0;
    if (samplingrate < 0 || (result = rtlsdr_set_sample_rate(device, (uint32_t) samplingrate)) < 0) {
        LOGI("ERROR: Failed to set sample rate to %lld", samplingrate);
        // LIBUSB_ERROR_IO is -1
        // LIBUSB_ERROR_TIMEOUT is -7
        if (result == -1 || result == -7) {
            RUN_OR(EXIT_NOT_ENOUGH_POWER, goto err);
        } else {
            RUN_OR(EXIT_WRONG_ARGS, goto err);
        }
    } else {
        LOGI("Set sampling rate to %lld", samplingrate);
    }

    if (frequency < 0 || rtlsdr_set_center_freq(device, (uint32_t) frequency) < 0) {
        LOGI("ERROR: Failed to frequency to %lld", frequency);
        RUN_OR(EXIT_WRONG_ARGS, goto err);
    }

    if (0 == gain) {
        if (rtlsdr_set_tuner_gain_mode(device, 0) < 0)
            LOGI("WARNING: Failed to enable automatic gain");
    } else {
        /* Enable manual gain */
        if (rtlsdr_set_tuner_gain_mode(device, 1) < 0)
            LOGI("WARNING: Failed to enable manual gain");

        if (rtlsdr_set_tuner_gain(device, gain) < 0)
            LOGI("WARNING: Failed to set tuner gain");
        else
            LOGI("Tuner gain set to %f dB", gain/10.0);
    }

    if (rtlsdr_reset_buffer(device) < 0)
        LOGI("WARNING: Failed to reset buffers");

    if (!sdrtcp_open_socket(&dev->tcpserv, address, port, "RTL0", rtlsdr_get_tuner_type(device), (uint32_t) rtlsdr_get_tuner_gains(device, NULL))) {
        RUN_OR(EXIT_WRONG_ARGS, goto err);
    }

    dev->rtl_dev = device;
    sdrtcp_serve_client_async(&dev->tcpserv, (void *) dev, tcpCommandCallback, tcpClosedCallback);

    int succesful = 1;
    EXCEPT_DO((*env)->CallVoidMethod(env, instance, announceOnOpen), succesful  = 0);
    if (rtlsdr_read_async(device, rtlsdr_callback, (void *) dev, 0, 0)) {
        LOGI("rtlsdr_read_async failed");
        succesful = 0;
    }
    LOGI("rtlsdr_read_async finished successfully");

    dev->rtl_dev = NULL;
    rtlsdr_close(device);
    sdrtcp_stop_serving_client(&dev->tcpserv);

    (*env)->ReleaseStringUTFChars(env, address_, address);
    (*env)->ReleaseStringUTFChars(env, devicePath_, devicePath);

    return succesful ? ((jboolean) JNI_TRUE) : ((jboolean) JNI_FALSE);

err:
    rtlsdr_close(device);

rel_jni:
    (*env)->ReleaseStringUTFChars(env, address_, address);
    (*env)->ReleaseStringUTFChars(env, devicePath_, devicePath);

    return (jboolean) JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_sdrtouch_rtlsdr_driver_RtlSdrDevice_initialize(JNIEnv *env, jobject instance) {
    rtlsdr_android_t* ptr = malloc(sizeof(rtlsdr_android_t));
    sdrtcp_init(&ptr->tcpserv);
    ptr->rtl_dev = NULL;
    return (jlong) ptr;
}

JNIEXPORT void JNICALL
Java_com_sdrtouch_rtlsdr_driver_RtlSdrDevice_deInit(JNIEnv *env, jobject instance, jlong pointer) {
    WITH_DEV(dev);
    sdrtcp_free(&dev->tcpserv);
    if (dev->rtl_dev != NULL) {
        rtlsdr_close(dev->rtl_dev);
        dev->rtl_dev = NULL;
    }
    free((void *) dev);
}

JNIEXPORT void JNICALL
Java_com_sdrtouch_rtlsdr_driver_RtlSdrDevice_close__J(JNIEnv *env, jobject instance,
                                                      jlong pointer) {
    WITH_DEV(dev);
    sdrtcp_stop_serving_client(&dev->tcpserv);
}

JNIEXPORT jobjectArray JNICALL Java_com_sdrtouch_rtlsdr_driver_RtlSdrDevice_getSupportedCommands(JNIEnv *env, jobject instance) {
    jint * commands = (jint *) SUPPORTED_COMMANDS;
    int n_commands = sizeof(SUPPORTED_COMMANDS) / sizeof(SUPPORTED_COMMANDS[0]);

    jintArray result;
    result = (*env)->NewIntArray(env, n_commands);
    if (result == NULL) return NULL;

    (*env)->SetIntArrayRegion(env, result, 0, n_commands, commands);
    return result;
}

int globalFD;
char * globalDevicePath;

JNIEXPORT void JNICALL
Java_com_sdrtouch_rtlsdr_StreamActivity_passFDandDeviceName(JNIEnv *env, jobject instance,jint fd_,jstring path_)
{
    //set up some dirty global variables found just above this metohd
    globalFD = fd_;
    globalDevicePath = (*env)->GetStringUTFChars(env, path_, 0);

}

JNIEXPORT void JNICALL
Java_com_sdrtouch_rtlsdr_StreamActivity_staphRTLPOWER(JNIEnv *env, jobject instance)
{
    do_exit=1;
}

static volatile int executionFinished = 0;

JNIEXPORT jstring JNICALL
Java_com_sdrtouch_rtlsdr_StreamActivity_stringFromJNI( JNIEnv* env, jobject object, jobjectArray stringArray)
{
    //Call main function for rtl_power
    // your argc
    // Get the number of args
    jsize ArgCount = (*env)->GetArrayLength(env, stringArray);
    // malloc the array of char* to be passed to the legacy main
    char ** argv = malloc(sizeof(char*)*(ArgCount+1)); // +1 for fake program name at index 0
    argv[ 0 ] = "MyProgramName";

    int i;
    for ( i = 0; i < ArgCount; ++i ) {
        jstring string = (jstring)((*env)->GetObjectArrayElement(env, stringArray, i));
        const char *cstring = (*env)->GetStringUTFChars(env, string, 0);
        argv[ i + 1 ] = strdup( cstring );
        (*env)->ReleaseStringUTFChars(env, string, cstring );
        (*env)->DeleteLocalRef(env, string );
    }

    // call the legacy "main" function
    executionFinished = 0;
    mainCOPIED( ArgCount + 1, argv );
	executionFinished = 1;

    // cleanup
    for( i = 0; i < ArgCount; ++i ) free( argv[ i + 1 ] );
    free( argv );

    //quan 123 end

    return (*env)->NewStringUTF(env, "Hello from JNI !\n");
}

JNIEXPORT jint JNICALL
Java_com_sdrtouch_rtlsdr_StreamActivity_readExecutionFinished(JNIEnv *env, jobject instance)
{
    return executionFinished;
}

void testThis()
{
    int someNumber=0;
    unsigned int sleepforthislong=2;
    while (someNumber != 200)
    {
        LOGI("fuck me\n");
        sleep(sleepforthislong);
        someNumber += sleepforthislong;
    }

}

//below is a copy paste from convenience

int verbose_auto_gain(rtlsdr_dev_t *dev);

/*
 * Copyright (C) 2014 by Kyle Keen <keenerd@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/* a collection of user friendly tools */

/*!
 * Convert standard suffixes (k, M, G) to double
 *
 * \param s a string to be parsed
 * \return double
 */

double atofs(char *s);

/*!
 * Convert time suffixes (s, m, h) to double
 *
 * \param s a string to be parsed
 * \return seconds as double
 */

double atoft(char *s);

/*!
 * Convert percent suffixe (%) to double
 *
 * \param s a string to be parsed
 * \return double
 */

double atofp(char *s);

/*!
 * Find nearest supported gain
 *
 * \param dev the device handle given by rtlsdr_open()
 * \param target_gain in tenths of a dB
 * \return 0 on success
 */

int nearest_gain(rtlsdr_dev_t *dev, int target_gain);

/*!
 * Set device frequency and report status on stderr
 *
 * \param dev the device handle given by rtlsdr_open()
 * \param frequency in Hz
 * \return 0 on success
 */

int verbose_set_frequency(rtlsdr_dev_t *dev, uint32_t frequency);

/*!
 * Set device sample rate and report status on stderr
 *
 * \param dev the device handle given by rtlsdr_open()
 * \param samp_rate in samples/second
 * \return 0 on success
 */

int verbose_set_sample_rate(rtlsdr_dev_t *dev, uint32_t samp_rate);

/*!
 * Enable or disable the direct sampling mode and report status on stderr
 *
 * \param dev the device handle given by rtlsdr_open()
 * \param on 0 means disabled, 1 I-ADC input enabled, 2 Q-ADC input enabled
 * \return 0 on success
 */

int verbose_direct_sampling(rtlsdr_dev_t *dev, int on);

/*!
 * Enable offset tuning and report status on stderr
 *
 * \param dev the device handle given by rtlsdr_open()
 * \return 0 on success
 */

int verbose_offset_tuning(rtlsdr_dev_t *dev);

/*!
 * Enable auto gain and report status on stderr
 *
 * \param dev the device handle given by rtlsdr_open()
 * \return 0 on success
 */

int verbose_auto_gain(rtlsdr_dev_t *dev);

/*!
 * Set tuner gain and report status on stderr
 *
 * \param dev the device handle given by rtlsdr_open()
 * \param gain in tenths of a dB
 * \return 0 on success
 */

int verbose_gain_set(rtlsdr_dev_t *dev, int gain);

/*!
 * Set the frequency correction value for the device and report status on stderr.
 *
 * \param dev the device handle given by rtlsdr_open()
 * \param ppm_error correction value in parts per million (ppm)
 * \return 0 on success
 */

int verbose_ppm_set(rtlsdr_dev_t *dev, int ppm_error);

/*!
 * Reset buffer
 *
 * \param dev the device handle given by rtlsdr_open()
 * \return 0 on success
 */

int verbose_reset_buffer(rtlsdr_dev_t *dev);

/*!
 * Find the closest matching device.
 *
 * \param s a string to be parsed
 * \return dev_index int, -1 on error
 */

int verbose_device_search(char *s);

/*
 * Copyright (C) 2014 by Kyle Keen <keenerd@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/* a collection of user friendly tools
 * todo: use strtol for more flexible int parsing
 * */

#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#ifndef _WIN32
#include <unistd.h>
#else
#include <windows.h>
#include <fcntl.h>
#include <io.h>
#define _USE_MATH_DEFINES
#endif

#include <math.h>

#include "rtl-sdr.h"

double atofs(char *s)
/* standard suffixes */
{
    char last;
    int len;
    double suff = 1.0;
    len = strlen(s);
    last = s[len-1];
    s[len-1] = '\0';
    switch (last) {
        case 'g':
        case 'G':
            suff *= 1e3;
        case 'm':
        case 'M':
            suff *= 1e3;
        case 'k':
        case 'K':
            suff *= 1e3;
            suff *= atof(s);
            s[len-1] = last;
            return suff;
    }
    s[len-1] = last;
    return atof(s);
}

double atoft(char *s)
/* time suffixes, returns seconds */
{
    char last;
    int len;
    double suff = 1.0;
    len = strlen(s);
    last = s[len-1];
    s[len-1] = '\0';
    switch (last) {
        case 'h':
        case 'H':
            suff *= 60;
        case 'm':
        case 'M':
            suff *= 60;
        case 's':
        case 'S':
            suff *= atof(s);
            s[len-1] = last;
            return suff;
    }
    s[len-1] = last;
    return atof(s);
}

double atofp(char *s)
/* percent suffixes */
{
    char last;
    int len;
    double suff = 1.0;
    len = strlen(s);
    last = s[len-1];
    s[len-1] = '\0';
    switch (last) {
        case '%':
            suff *= 0.01;
            suff *= atof(s);
            s[len-1] = last;
            return suff;
    }
    s[len-1] = last;
    return atof(s);
}

int nearest_gain(rtlsdr_dev_t *dev, int target_gain)
{
    int i, r, err1, err2, count, nearest;
    int* gains;
    r = rtlsdr_set_tuner_gain_mode(dev, 1);
    if (r < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "WARNING: Failed to enable manual gain.\n");
        return r;
    }
    count = rtlsdr_get_tuner_gains(dev, NULL);
    if (count <= 0) {
        return 0;
    }
    gains = malloc(sizeof(int) * count);
    count = rtlsdr_get_tuner_gains(dev, gains);
    nearest = gains[0];
    for (i=0; i<count; i++) {
        err1 = abs(target_gain - nearest);
        err2 = abs(target_gain - gains[i]);
        if (err2 < err1) {
            nearest = gains[i];
        }
    }
    free(gains);
    return nearest;
}

int verbose_set_frequency(rtlsdr_dev_t *dev, uint32_t frequency)
{
    int r;
    r = rtlsdr_set_center_freq(dev, frequency);
    if (r < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "WARNING: Failed to set center freq.\n");
    } else {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Tuned to %u Hz.\n", frequency);
    }
    return r;
}

int verbose_set_sample_rate(rtlsdr_dev_t *dev, uint32_t samp_rate)
{
    int r;
    r = rtlsdr_set_sample_rate(dev, samp_rate);
    if (r < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "WARNING: Failed to set sample rate.\n");
    } else {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Sampling at %u S/s.\n", samp_rate);
    }
    return r;
}

int verbose_direct_sampling(rtlsdr_dev_t *dev, int on)
{
    int r;
    r = rtlsdr_set_direct_sampling(dev, on);
    if (r != 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "WARNING: Failed to set direct sampling mode.\n");
        return r;
    }
    if (on == 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Direct sampling mode disabled.\n");}
    if (on == 1) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Enabled direct sampling mode, input 1/I.\n");}
    if (on == 2) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Enabled direct sampling mode, input 2/Q.\n");}
    return r;
}

int verbose_offset_tuning(rtlsdr_dev_t *dev)
{
    int r;
    r = rtlsdr_set_offset_tuning(dev, 1);
    if (r != 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "WARNING: Failed to set offset tuning.\n");
    } else {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Offset tuning mode enabled.\n");
    }
    return r;
}



int verbose_gain_set(rtlsdr_dev_t *dev, int gain)
{
    int r;
    r = rtlsdr_set_tuner_gain_mode(dev, 1);
    if (r < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "WARNING: Failed to enable manual gain.\n");
        return r;
    }
    r = rtlsdr_set_tuner_gain(dev, gain);
    if (r != 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "WARNING: Failed to set tuner gain.\n");
    } else {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Tuner gain set to %0.2f dB.\n", gain/10.0);
    }
    return r;
}

int verbose_ppm_set(rtlsdr_dev_t *dev, int ppm_error)
{
    int r;
    if (ppm_error == 0) {
        return 0;}
    r = rtlsdr_set_freq_correction(dev, ppm_error);
    if (r < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "WARNING: Failed to set ppm error.\n");
    } else {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Tuner error set to %i ppm.\n", ppm_error);
    }
    return r;
}

int verbose_reset_buffer(rtlsdr_dev_t *dev)
{
    int r;
    r = rtlsdr_reset_buffer(dev);
    if (r < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "WARNING: Failed to reset buffers.\n");}
    return r;
}

int verbose_device_search(char *s)
{
    int i, device_count, device, offset;
    char *s2;
    char vendor[256], product[256], serial[256];
    device_count = rtlsdr_get_device_count();
    if (!device_count) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "No supported devices found.\n");
        return -1;
    }
    __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Found %d device(s):\n", device_count);
    for (i = 0; i < device_count; i++) {
        rtlsdr_get_device_usb_strings(i, vendor, product, serial);
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "  %d:  %s, %s, SN: %s\n", i, vendor, product, serial);
    }
    /* does string look like raw id number */
    device = (int)strtol(s, &s2, 0);
    if (s2[0] == '\0' && device >= 0 && device < device_count) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Using device %d: %s\n",
                device, rtlsdr_get_device_name((uint32_t)device));
        return device;
    }
    /* does string exact match a serial */
    for (i = 0; i < device_count; i++) {
        rtlsdr_get_device_usb_strings(i, vendor, product, serial);
        if (strcmp(s, serial) != 0) {
            continue;}
        device = i;
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Using device %d: %s\n",
                device, rtlsdr_get_device_name((uint32_t)device));
        return device;
    }
    /* does string prefix match a serial */
    for (i = 0; i < device_count; i++) {
        rtlsdr_get_device_usb_strings(i, vendor, product, serial);
        if (strncmp(s, serial, strlen(s)) != 0) {
            continue;}
        device = i;
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Using device %d: %s\n",
                device, rtlsdr_get_device_name((uint32_t)device));
        return device;
    }
    /* does string suffix match a serial */
    for (i = 0; i < device_count; i++) {
        rtlsdr_get_device_usb_strings(i, vendor, product, serial);
        offset = strlen(serial) - strlen(s);
        if (offset < 0) {
            continue;}
        if (strncmp(s, serial+offset, strlen(s)) != 0) {
            continue;}
        device = i;
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Using device %d: %s\n",
                device, rtlsdr_get_device_name((uint32_t)device));
        return device;
    }
    __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "No matching devices found.\n");
    return -1;
}

// vim: tabstop=8:softtabstop=8:shiftwidth=8:noexpandtab


// below is a copy paste from rtl_power

/*
 * rtl-sdr, turns your Realtek RTL2832 based DVB dongle into a SDR receiver
 * Copyright (C) 2012 by Steve Markgraf <steve@steve-m.de>
 * Copyright (C) 2012 by Hoernchen <la@tfc-server.de>
 * Copyright (C) 2012 by Kyle Keen <keenerd@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


/*
 * rtl_power: general purpose FFT integrator
 * -f low_freq:high_freq:max_bin_size
 * -i seconds
 * outputs CSV
 * time, low, high, step, db, db, db ...
 * db optional?  raw output might be better for noise correction
 * todo:
 *	threading
 *	randomized hopping
 *	noise correction
 *	continuous IIR
 *	general astronomy usefulness
 *	multiple dongles
 *	multiple FFT workers
 *	check edge cropping for off-by-one and rounding errors
 *	1.8MS/s for hiding xtal harmonics
 */

#include <errno.h>
#include <signal.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#ifndef _WIN32
#include <unistd.h>
#else
#include <windows.h>
#include <fcntl.h>
#include <io.h>
#include "getopt/getopt.h"
#define usleep(x) Sleep(x/1000)
#if defined(_MSC_VER) && (_MSC_VER < 1800)
#define round(x) (x > 0.0 ? floor(x + 0.5): ceil(x - 0.5))
#endif
#define _USE_MATH_DEFINES
#endif

#include <math.h>
#include <pthread.h>
#include <libusb.h>

#include "rtl-sdr.h"

#define MAX(x, y) (((x) > (y)) ? (x) : (y))

#define DEFAULT_BUF_LENGTH		(1 * 16384)
#define AUTO_GAIN			-100
#define BUFFER_DUMP			(1<<12)

#define MAXIMUM_RATE			2800000
#define MINIMUM_RATE			1000000

static rtlsdr_dev_t *dev = NULL;
FILE *file;

int16_t* Sinewave;
double* power_table;
int N_WAVE, LOG2_N_WAVE;
int next_power;
int16_t *fft_buf;
int *window_coefs;

struct tuning_state
/* one per tuning range */
{
    int freq;
    int rate;
    int bin_e;
    long *avg;  /* length == 2^bin_e */
    int samples;
    int downsample;
    int downsample_passes;  /* for the recursive filter */
    double crop;
    //pthread_rwlock_t avg_lock;
    //pthread_mutex_t avg_mutex;
    /* having the iq buffer here is wasteful, but will avoid contention */
    uint8_t *buf8;
    int buf_len;
    //int *comp_fir;
    //pthread_rwlock_t buf_lock;
    //pthread_mutex_t buf_mutex;
};

/* 3000 is enough for 3GHz b/w worst case */
#define MAX_TUNES	3000
struct tuning_state tunes[MAX_TUNES];
int tune_count = 0;

int boxcar = 1;
int comp_fir_size = 0;
int peak_hold = 0;

void usage(void)
{
    __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG",
            "rtl_power, a simple FFT logger for RTL2832 based DVB-T receivers\n\n"
                    "Use:\trtl_power -f freq_range [-options] [filename]\n"
                    "\t-f lower:upper:bin_size [Hz]\n"
                    "\t (bin size is a maximum, smaller more convenient bins\n"
                    "\t  will be used.  valid range 1Hz - 2.8MHz)\n"
                    "\t[-i integration_interval (default: 10 seconds)]\n"
                    "\t (buggy if a full sweep takes longer than the interval)\n"
                    "\t[-1 enables single-shot mode (default: off)]\n"
                    "\t[-e exit_timer (default: off/0)]\n"
                    //"\t[-s avg/iir smoothing (default: avg)]\n"
                    //"\t[-t threads (default: 1)]\n"
                    "\t[-d device_index (default: 0)]\n"
                    "\t[-g tuner_gain (default: automatic)]\n"
                    "\t[-p ppm_error (default: 0)]\n"
                    "\tfilename (a '-' dumps samples to stdout)\n"
                    "\t (omitting the filename also uses stdout)\n"
                    "\n"
                    "Experimental options:\n"
                    "\t[-w window (default: rectangle)]\n"
                    "\t (hamming, blackman, blackman-harris, hann-poisson, bartlett, youssef)\n"
                    // kaiser
                    "\t[-c crop_percent (default: 0%%, recommended: 20%%-50%%)]\n"
                    "\t (discards data at the edges, 100%% discards everything)\n"
                    "\t (has no effect for bins larger than 1MHz)\n"
                    "\t[-F fir_size (default: disabled)]\n"
                    "\t (enables low-leakage downsample filter,\n"
                    "\t  fir_size can be 0 or 9.  0 has bad roll off,\n"
                    "\t  try with '-c 50%%')\n"
                    "\t[-P enables peak hold (default: off)]\n"
                    "\t[-D enable direct sampling (default: off)]\n"
                    "\t[-O enable offset tuning (default: off)]\n"
                    "\n"
                    "CSV FFT output columns:\n"
                    "\tdate, time, Hz low, Hz high, Hz step, samples, dbm, dbm, ...\n\n"
                    "Examples:\n"
                    "\trtl_power -f 88M:108M:125k fm_stations.csv\n"
                    "\t (creates 160 bins across the FM band,\n"
                    "\t  individual stations should be visible)\n"
                    "\trtl_power -f 100M:1G:1M -i 5m -1 survey.csv\n"
                    "\t (a five minute low res scan of nearly everything)\n"
                    "\trtl_power -f ... -i 15m -1 log.csv\n"
                    "\t (integrate for 15 minutes and exit afterwards)\n"
                    "\trtl_power -f ... -e 1h | gzip > log.csv.gz\n"
                    "\t (collect data for one hour and compress it on the fly)\n\n"
                    "Convert CSV to a waterfall graphic with:\n"
                    "\t http://kmkeen.com/tmp/heatmap.py.txt \n");
    //exit(1);
    return;
}

void multi_bail(void)
{
    if (do_exit == 1)
    {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Signal caught, finishing scan pass.\n");
    }
    if (do_exit >= 2)
    {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Signal caught, aborting immediately.\n");
    }
}

#ifdef _WIN32
BOOL WINAPI
sighandler(int signum)
{
	if (CTRL_C_EVENT == signum) {
		do_exit++;
		multi_bail();
		return TRUE;
	}
	return FALSE;
}
#else
static void sighandler(int signum)
{
    do_exit++;
    multi_bail();
}
#endif

/* more cond dumbness */
#define safe_cond_signal(n, m) pthread_mutex_lock(m); pthread_cond_signal(n); pthread_mutex_unlock(m)
#define safe_cond_wait(n, m) pthread_mutex_lock(m); pthread_cond_wait(n, m); pthread_mutex_unlock(m)

/* {length, coef, coef, coef}  and scaled by 2^15
   for now, only length 9, optimal way to get +85% bandwidth */
#define CIC_TABLE_MAX 10
int cic_9_tables[][10] = {
        {0,},
        {9, -156,  -97, 2798, -15489, 61019, -15489, 2798,  -97, -156},
        {9, -128, -568, 5593, -24125, 74126, -24125, 5593, -568, -128},
        {9, -129, -639, 6187, -26281, 77511, -26281, 6187, -639, -129},
        {9, -122, -612, 6082, -26353, 77818, -26353, 6082, -612, -122},
        {9, -120, -602, 6015, -26269, 77757, -26269, 6015, -602, -120},
        {9, -120, -582, 5951, -26128, 77542, -26128, 5951, -582, -120},
        {9, -119, -580, 5931, -26094, 77505, -26094, 5931, -580, -119},
        {9, -119, -578, 5921, -26077, 77484, -26077, 5921, -578, -119},
        {9, -119, -577, 5917, -26067, 77473, -26067, 5917, -577, -119},
        {9, -199, -362, 5303, -25505, 77489, -25505, 5303, -362, -199},
};

#if defined(_MSC_VER) && (_MSC_VER < 1800)
double log2(double n)
{
	return log(n) / log(2.0);
}
#endif

/* FFT based on fix_fft.c by Roberts, Slaney and Bouras
   http://www.jjj.de/fft/fftpage.html
   16 bit ints for everything
   -32768..+32768 maps to -1.0..+1.0
*/

void sine_table(int size)
{
    int i;
    double d;
    LOG2_N_WAVE = size;
    N_WAVE = 1 << LOG2_N_WAVE;
    Sinewave = malloc(sizeof(int16_t) * N_WAVE*3/4);
    power_table = malloc(sizeof(double) * N_WAVE);
    for (i=0; i<N_WAVE*3/4; i++)
    {
        d = (double)i * 2.0 * M_PI / N_WAVE;
        Sinewave[i] = (int)round(32767*sin(d));
        //printf("%i\n", Sinewave[i]);
    }
}

inline int16_t FIX_MPY(int16_t a, int16_t b)
/* fixed point multiply and scale */
{
    int c = ((int)a * (int)b) >> 14;
    b = c & 0x01;
    return (c >> 1) + b;
}

int fix_fft(int16_t iq[], int m)
/* interleaved iq[], 0 <= n < 2**m, changes in place */
{
    int mr, nn, i, j, l, k, istep, n, shift;
    int16_t qr, qi, tr, ti, wr, wi;
    n = 1 << m;
    if (n > N_WAVE)
    {return -1;}
    mr = 0;
    nn = n - 1;
    /* decimation in time - re-order data */
    for (m=1; m<=nn; ++m) {
        l = n;
        do
        {l >>= 1;}
        while (mr+l > nn);
        mr = (mr & (l-1)) + l;
        if (mr <= m)
        {continue;}
        // real = 2*m, imag = 2*m+1
        tr = iq[2*m];
        iq[2*m] = iq[2*mr];
        iq[2*mr] = tr;
        ti = iq[2*m+1];
        iq[2*m+1] = iq[2*mr+1];
        iq[2*mr+1] = ti;
    }
    l = 1;
    k = LOG2_N_WAVE-1;
    while (l < n) {
        shift = 1;
        istep = l << 1;
        for (m=0; m<l; ++m) {
            j = m << k;
            wr =  Sinewave[j+N_WAVE/4];
            wi = -Sinewave[j];
            if (shift) {
                wr >>= 1; wi >>= 1;}
            for (i=m; i<n; i+=istep) {
                j = i + l;
                tr = FIX_MPY(wr,iq[2*j]) - FIX_MPY(wi,iq[2*j+1]);
                ti = FIX_MPY(wr,iq[2*j+1]) + FIX_MPY(wi,iq[2*j]);
                qr = iq[2*i];
                qi = iq[2*i+1];
                if (shift) {
                    qr >>= 1; qi >>= 1;}
                iq[2*j] = qr - tr;
                iq[2*j+1] = qi - ti;
                iq[2*i] = qr + tr;
                iq[2*i+1] = qi + ti;
            }
        }
        --k;
        l = istep;
    }
    return 0;
}

double rectangle(int i, int length)
{
    return 1.0;
}

double hamming(int i, int length)
{
    double a, b, w, N1;
    a = 25.0/46.0;
    b = 21.0/46.0;
    N1 = (double)(length-1);
    w = a - b*cos(2*i*M_PI/N1);
    return w;
}

double blackman(int i, int length)
{
    double a0, a1, a2, w, N1;
    a0 = 7938.0/18608.0;
    a1 = 9240.0/18608.0;
    a2 = 1430.0/18608.0;
    N1 = (double)(length-1);
    w = a0 - a1*cos(2*i*M_PI/N1) + a2*cos(4*i*M_PI/N1);
    return w;
}

double blackman_harris(int i, int length)
{
    double a0, a1, a2, a3, w, N1;
    a0 = 0.35875;
    a1 = 0.48829;
    a2 = 0.14128;
    a3 = 0.01168;
    N1 = (double)(length-1);
    w = a0 - a1*cos(2*i*M_PI/N1) + a2*cos(4*i*M_PI/N1) - a3*cos(6*i*M_PI/N1);
    return w;
}

double hann_poisson(int i, int length)
{
    double a, N1, w;
    a = 2.0;
    N1 = (double)(length-1);
    w = 0.5 * (1 - cos(2*M_PI*i/N1)) * \
	    pow(M_E, (-a*(double)abs((int)(N1-1-2*i)))/N1);
    return w;
}

double youssef(int i, int length)
/* really a blackman-harris-poisson window, but that is a mouthful */
{
    double a, a0, a1, a2, a3, w, N1;
    a0 = 0.35875;
    a1 = 0.48829;
    a2 = 0.14128;
    a3 = 0.01168;
    N1 = (double)(length-1);
    w = a0 - a1*cos(2*i*M_PI/N1) + a2*cos(4*i*M_PI/N1) - a3*cos(6*i*M_PI/N1);
    a = 0.0025;
    w *= pow(M_E, (-a*(double)abs((int)(N1-1-2*i)))/N1);
    return w;
}

double kaiser(int i, int length)
// todo, become more smart
{
    return 1.0;
}

double bartlett(int i, int length)
{
    double N1, L, w;
    L = (double)length;
    N1 = L - 1;
    w = (i - N1/2) / (L/2);
    if (w < 0) {
        w = -w;}
    w = 1 - w;
    return w;
}

void rms_power(struct tuning_state *ts)
/* for bins between 1MHz and 2MHz */
{
    int i, s;
    uint8_t *buf = ts->buf8;
    int buf_len = ts->buf_len;
    long p, t;
    double dc, err;

    p = t = 0L;
    for (i=0; i<buf_len; i++) {
        s = (int)buf[i] - 127;
        t += (long)s;
        p += (long)(s * s);
    }
    /* correct for dc offset in squares */
    dc = (double)t / (double)buf_len;
    err = t * 2 * dc - dc * dc * buf_len;
    p -= (long)round(err);

    if (!peak_hold) {
        ts->avg[0] += p;
    } else {
        ts->avg[0] = MAX(ts->avg[0], p);
    }
    ts->samples += 1;
}

void frequency_range(char *arg, double crop)
/* flesh out the tunes[] for scanning */
// do we want the fewest ranges (easy) or the fewest bins (harder)?
{
    char *start, *stop, *step;
    int i, j, upper, lower, max_size, bw_seen, bw_used, bin_e, buf_len;
    int downsample, downsample_passes;
    double bin_size;
    struct tuning_state *ts;
    /* hacky string parsing */
    start = arg;
    stop = strchr(start, ':') + 1;
    stop[-1] = '\0';
    step = strchr(stop, ':') + 1;
    step[-1] = '\0';
    lower = (int)atofs(start);
    upper = (int)atofs(stop);
    max_size = (int)atofs(step);
    stop[-1] = ':';
    step[-1] = ':';
    downsample = 1;
    downsample_passes = 0;
    /* evenly sized ranges, as close to MAXIMUM_RATE as possible */
    // todo, replace loop with algebra
    for (i=1; i<1500; i++) {
        bw_seen = (upper - lower) / i;
        bw_used = (int)((double)(bw_seen) / (1.0 - crop));
        if (bw_used > MAXIMUM_RATE) {
            continue;}
        tune_count = i;
        break;
    }
    /* unless small bandwidth */
    if (bw_used < MINIMUM_RATE) {
        tune_count = 1;
        downsample = MAXIMUM_RATE / bw_used;
        bw_used = bw_used * downsample;
    }
    if (!boxcar && downsample > 1) {
        downsample_passes = (int)log2(downsample);
        downsample = 1 << downsample_passes;
        bw_used = (int)((double)(bw_seen * downsample) / (1.0 - crop));
    }
    /* number of bins is power-of-two, bin size is under limit */
    // todo, replace loop with log2
    for (i=1; i<=21; i++) {
        bin_e = i;
        bin_size = (double)bw_used / (double)((1<<i) * downsample);
        if (bin_size <= (double)max_size) {
            break;}
    }
    /* unless giant bins */
    if (max_size >= MINIMUM_RATE) {
        bw_seen = max_size;
        bw_used = max_size;
        tune_count = (upper - lower) / bw_seen;
        bin_e = 0;
        crop = 0;
    }
    if (tune_count > MAX_TUNES) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Error: bandwidth too wide.\n");
        //exit(1);
        return;
    }
    buf_len = 2 * (1<<bin_e) * downsample;
    if (buf_len < DEFAULT_BUF_LENGTH) {
        buf_len = DEFAULT_BUF_LENGTH;
    }
    /* build the array */
    for (i=0; i<tune_count; i++) {
        ts = &tunes[i];
        ts->freq = lower + i*bw_seen + bw_seen/2;
        ts->rate = bw_used;
        ts->bin_e = bin_e;
        ts->samples = 0;
        ts->crop = crop;
        ts->downsample = downsample;
        ts->downsample_passes = downsample_passes;
        ts->avg = (long*)malloc((1<<bin_e) * sizeof(long));
        if (!ts->avg) {
            __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Error: malloc.\n");
            //exit(1);
            return;
        }
        for (j=0; j<(1<<bin_e); j++) {
            ts->avg[j] = 0L;
        }
        ts->buf8 = (uint8_t*)malloc(buf_len * sizeof(uint8_t));
        if (!ts->buf8) {
            __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Error: malloc.\n");
            //exit(1);
            return;
        }
        ts->buf_len = buf_len;
    }
    /* report */
    __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Number of frequency hops: %i\n", tune_count);
    __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Dongle bandwidth: %iHz\n", bw_used);
    __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Downsampling by: %ix\n", downsample);
    __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Cropping by: %0.2f%%\n", crop*100);
    __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Total FFT bins: %i\n", tune_count * (1<<bin_e));
    __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Logged FFT bins: %i\n", \
	  (int)((double)(tune_count * (1<<bin_e)) * (1.0-crop)));
    __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "FFT bin size: %0.2fHz\n", bin_size);
    __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Buffer size: %i bytes (%0.2fms)\n", buf_len, 1000 * 0.5 * (float)buf_len / (float)bw_used);
}

void retune(rtlsdr_dev_t *d, int freq)
{
    uint8_t dump[BUFFER_DUMP];
    int n_read;
    rtlsdr_set_center_freq(d, (uint32_t)freq);
    /* wait for settling and flush buffer */
    usleep(5000);
    rtlsdr_read_sync(d, &dump, BUFFER_DUMP, &n_read);
    if (n_read != BUFFER_DUMP) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Error: bad retune.\n");}
}

void fifth_order(int16_t *data, int length)
/* for half of interleaved data */
{
    int i;
    int a, b, c, d, e, f;
    a = data[0];
    b = data[2];
    c = data[4];
    d = data[6];
    e = data[8];
    f = data[10];
    /* a downsample should improve resolution, so don't fully shift */
    /* ease in instead of being stateful */
    data[0] = ((a+b)*10 + (c+d)*5 + d + f) >> 4;
    data[2] = ((b+c)*10 + (a+d)*5 + e + f) >> 4;
    data[4] = (a + (b+e)*5 + (c+d)*10 + f) >> 4;
    for (i=12; i<length; i+=4) {
        a = c;
        b = d;
        c = e;
        d = f;
        e = data[i-2];
        f = data[i];
        data[i/2] = (a + (b+e)*5 + (c+d)*10 + f) >> 4;
    }
}

void remove_dc(int16_t *data, int length)
/* works on interleaved data */
{
    int i;
    int16_t ave;
    long sum = 0L;
    for (i=0; i < length; i+=2) {
        sum += data[i];
    }
    ave = (int16_t)(sum / (long)(length));
    if (ave == 0) {
        return;}
    for (i=0; i < length; i+=2) {
        data[i] -= ave;
    }
}

void generic_fir(int16_t *data, int length, int *fir)
/* Okay, not at all generic.  Assumes length 9, fix that eventually. */
{
    int d, temp, sum;
    int hist[9] = {0,};
    /* cheat on the beginning, let it go unfiltered */
    for (d=0; d<18; d+=2) {
        hist[d/2] = data[d];
    }
    for (d=18; d<length; d+=2) {
        temp = data[d];
        sum = 0;
        sum += (hist[0] + hist[8]) * fir[1];
        sum += (hist[1] + hist[7]) * fir[2];
        sum += (hist[2] + hist[6]) * fir[3];
        sum += (hist[3] + hist[5]) * fir[4];
        sum +=            hist[4]  * fir[5];
        data[d] = (int16_t)(sum >> 15) ;
        hist[0] = hist[1];
        hist[1] = hist[2];
        hist[2] = hist[3];
        hist[3] = hist[4];
        hist[4] = hist[5];
        hist[5] = hist[6];
        hist[6] = hist[7];
        hist[7] = hist[8];
        hist[8] = temp;
    }
}

void downsample_iq(int16_t *data, int length)
{
    fifth_order(data, length);
    //remove_dc(data, length);
    fifth_order(data+1, length-1);
    //remove_dc(data+1, length-1);
}

long real_conj(int16_t real, int16_t imag)
/* real(n * conj(n)) */
{
    return ((long)real*(long)real + (long)imag*(long)imag);
}

void scanner(void)
{
    int i, j, j2, f, n_read, offset, bin_e, bin_len, buf_len, ds, ds_p;
    int32_t w;
    struct tuning_state *ts;
    bin_e = tunes[0].bin_e;
    bin_len = 1 << bin_e;
    buf_len = tunes[0].buf_len;
    for (i=0; i<tune_count; i++) {
        if (do_exit >= 2)
        {return;}
        ts = &tunes[i];
        f = (int)rtlsdr_get_center_freq(dev);
        if (f != ts->freq) {
            retune(dev, ts->freq);}
        rtlsdr_read_sync(dev, ts->buf8, buf_len, &n_read);
        if (n_read != buf_len) {
            __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Error: dropped samples.\n");}
        /* rms */
        if (bin_len == 1) {
            rms_power(ts);
            continue;
        }
        /* prep for fft */
        for (j=0; j<buf_len; j++) {
            fft_buf[j] = (int16_t)ts->buf8[j] - 127;
        }
        ds = ts->downsample;
        ds_p = ts->downsample_passes;
        if (boxcar && ds > 1) {
            j=2, j2=0;
            while (j < buf_len) {
                fft_buf[j2]   += fft_buf[j];
                fft_buf[j2+1] += fft_buf[j+1];
                fft_buf[j] = 0;
                fft_buf[j+1] = 0;
                j += 2;
                if (j % (ds*2) == 0) {
                    j2 += 2;}
            }
        } else if (ds_p) {  /* recursive */
            for (j=0; j < ds_p; j++) {
                downsample_iq(fft_buf, buf_len >> j);
            }
            /* droop compensation */
            if (comp_fir_size == 9 && ds_p <= CIC_TABLE_MAX) {
                generic_fir(fft_buf, buf_len >> j, cic_9_tables[ds_p]);
                generic_fir(fft_buf+1, (buf_len >> j)-1, cic_9_tables[ds_p]);
            }
        }
        remove_dc(fft_buf, buf_len / ds);
        remove_dc(fft_buf+1, (buf_len / ds) - 1);
        /* window function and fft */
        for (offset=0; offset<(buf_len/ds); offset+=(2*bin_len)) {
            // todo, let rect skip this
            for (j=0; j<bin_len; j++) {
                w =  (int32_t)fft_buf[offset+j*2];
                w *= (int32_t)(window_coefs[j]);
                //w /= (int32_t)(ds);
                fft_buf[offset+j*2]   = (int16_t)w;
                w =  (int32_t)fft_buf[offset+j*2+1];
                w *= (int32_t)(window_coefs[j]);
                //w /= (int32_t)(ds);
                fft_buf[offset+j*2+1] = (int16_t)w;
            }
            fix_fft(fft_buf+offset, bin_e);
            if (!peak_hold) {
                for (j=0; j<bin_len; j++) {
                    ts->avg[j] += real_conj(fft_buf[offset+j*2], fft_buf[offset+j*2+1]);
                }
            } else {
                for (j=0; j<bin_len; j++) {
                    ts->avg[j] = MAX(real_conj(fft_buf[offset+j*2], fft_buf[offset+j*2+1]), ts->avg[j]);
                }
            }
            ts->samples += ds;
        }
    }
}

void csv_dbm(struct tuning_state *ts)
{
    int i, len, ds, i1, i2, bw2, bin_count;
    long tmp;
    double dbm;
    len = 1 << ts->bin_e;
    ds = ts->downsample;
    /* fix FFT stuff quirks */
    if (ts->bin_e > 0) {
        /* nuke DC component (not effective for all windows) */
        ts->avg[0] = ts->avg[1];
        /* FFT is translated by 180 degrees */
        for (i=0; i<len/2; i++) {
            tmp = ts->avg[i];
            ts->avg[i] = ts->avg[i+len/2];
            ts->avg[i+len/2] = tmp;
        }
    }
    /* Hz low, Hz high, Hz step, samples, dbm, dbm, ... */
    bin_count = (int)((double)len * (1.0 - ts->crop));
    bw2 = (int)(((double)ts->rate * (double)bin_count) / (len * 2 * ds));
    fprintf(file, "%i, %i, %.2f, %i, ", ts->freq - bw2, ts->freq + bw2,
            (double)ts->rate / (double)(len*ds), ts->samples);
    __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "printing Hz low, Hz high, Hz step, samples");

    // something seems off with the dbm math
    i1 = 0 + (int)((double)len * ts->crop * 0.5);
    i2 = (len-1) - (int)((double)len * ts->crop * 0.5);
    __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "printing individual samples");
    for (i=i1; i<=i2; i++) {
        dbm  = (double)ts->avg[i];
        dbm /= (double)ts->rate;
        dbm /= (double)ts->samples;
        dbm  = 10 * log10(dbm);
        fprintf(file, "%.2f, ", dbm);
    }
    dbm = (double)ts->avg[i2] / ((double)ts->rate * (double)ts->samples);
    if (ts->bin_e == 0) {
        dbm = ((double)ts->avg[0] / \
		((double)ts->rate * (double)ts->samples));}
    dbm  = 10 * log10(dbm);
    fprintf(file, "%.2f\n", dbm);
    __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "==== CSV row finished ====");
    for (i=0; i<len; i++) {
        ts->avg[i] = 0L;
    }
    ts->samples = 0;
}

int mainCOPIED(int argc, char **argv)
{
#ifndef _WIN32
    struct sigaction sigact;
#endif
    char *filename = NULL;
    int i, length, r, opt, wb_mode = 0;
    int f_set = 0;
    int gain = AUTO_GAIN; // tenths of a dB
    int dev_index = 0;
    int dev_given = 0;
    int ppm_error = 0;
    int interval = 10;
    int fft_threads = 1;
    int smoothing = 0;
    int single = 0;
    int direct_sampling = 0;
    int offset_tuning = 0;
    double crop = 0.0;
    char *freq_optarg;
    time_t next_tick;
    time_t time_now;
    time_t exit_time = 0;
    char t_str[50];
    struct tm *cal_time;
    double (*window_fn)(int, int) = rectangle;
    freq_optarg = "";

    while ((opt = getopt(argc, argv, "f:i:s:t:d:g:p:e:w:c:F:1PDOh")) != -1) {
        switch (opt) {
            case 'f': // lower:upper:bin_size
                freq_optarg = strdup(optarg);
                f_set = 1;
                break;
            case 'd':
                dev_index = verbose_device_search(optarg);
                dev_given = 1;
                break;
            case 'g':
                gain = (int)(atof(optarg) * 10);
                break;
            case 'c':
                crop = atofp(optarg);
                break;
            case 'i':
                interval = (int)round(atoft(optarg));
                break;
            case 'e':
                exit_time = (time_t)((int)round(atoft(optarg)));
                break;
            case 's':
                if (strcmp("avg",  optarg) == 0) {
                    smoothing = 0;}
                if (strcmp("iir",  optarg) == 0) {
                    smoothing = 1;}
                break;
            case 'w':
                if (strcmp("rectangle",  optarg) == 0) {
                    window_fn = rectangle;}
                if (strcmp("hamming",  optarg) == 0) {
                    window_fn = hamming;}
                if (strcmp("blackman",  optarg) == 0) {
                    window_fn = blackman;}
                if (strcmp("blackman-harris",  optarg) == 0) {
                    window_fn = blackman_harris;}
                if (strcmp("hann-poisson",  optarg) == 0) {
                    window_fn = hann_poisson;}
                if (strcmp("youssef",  optarg) == 0) {
                    window_fn = youssef;}
                if (strcmp("kaiser",  optarg) == 0) {
                    window_fn = kaiser;}
                if (strcmp("bartlett",  optarg) == 0) {
                    window_fn = bartlett;}
                break;
            case 't':
                fft_threads = atoi(optarg);
                break;
            case 'p':
                ppm_error = atoi(optarg);
                break;
            case '1':
                single = 1;
                break;
            case 'P':
                peak_hold = 1;
                break;
            case 'D':
                direct_sampling = 1;
                break;
            case 'O':
                offset_tuning = 1;
                break;
            case 'F':
                boxcar = 0;
                comp_fir_size = atoi(optarg);
                break;
            case 'h':
            default:
                usage();
                break;
        }
    }

    if (!f_set) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "No frequency range provided.\n");
        //exit(1);
        return 0;
    }

    if ((crop < 0.0) || (crop > 1.0)) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Crop value outside of 0 to 1.\n");
        //exit(1);
        return 0;
    }

    frequency_range(freq_optarg, crop);

    if (tune_count == 0) {
        usage();}

    if (argc <= optind) {
        filename = "-";
    } else {
        filename = argv[optind];
    }

    if (interval < 1) {
        interval = 1;}

    __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Reporting every %i seconds\n", interval);

    //the value of dev_given is set to TRUE (1) IF the user specified a value for argument -d
    //if (!dev_given) {
    //    dev_index = verbose_device_search("0");
    //}

    if (dev_index < 0) {
        //exit(1);
        return 0;
    }
    //original line commented out
    //r = rtlsdr_open(&dev, (uint32_t)dev_index);
    r = rtlsdr_open2(&dev, globalFD, globalDevicePath);
    if (r < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Failed to open rtlsdr device #%d.\n", dev_index);
        //exit(1);
        return 0;
    }
#ifndef _WIN32
    sigact.sa_handler = sighandler;
    sigemptyset(&sigact.sa_mask);
    sigact.sa_flags = 0;
    sigaction(SIGINT, &sigact, NULL);
    sigaction(SIGTERM, &sigact, NULL);
    sigaction(SIGQUIT, &sigact, NULL);
    sigaction(SIGPIPE, &sigact, NULL);
#else
    SetConsoleCtrlHandler( (PHANDLER_ROUTINE) sighandler, TRUE );
#endif

    if (direct_sampling) {
        verbose_direct_sampling(dev, 1);
    }

    if (offset_tuning) {
        verbose_offset_tuning(dev);
    }

    /* Set the tuner gain */
    if (gain == AUTO_GAIN) {
//        verbose_auto_gain(dev);
    } else {
        gain = nearest_gain(dev, gain);
  //      verbose_gain_set(dev, gain);
    }

    verbose_ppm_set(dev, ppm_error);

    if (strcmp(filename, "-") == 0) { /* Write log to stdout */
        file = stdout;
#ifdef _WIN32
        // Is this necessary?  Output is ascii.
		_setmode(_fileno(file), _O_BINARY);
#endif
    } else {
        file = fopen(filename, "wb");
        if (!file) {
            __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Failed to open %s\n", filename);
            //exit(1);
            return 0;
        }
    }

    /* Reset endpoint before we start reading from it (mandatory) */
    verbose_reset_buffer(dev);

    /* actually do stuff */
    rtlsdr_set_sample_rate(dev, (uint32_t)tunes[0].rate);
    sine_table(tunes[0].bin_e);
    next_tick = time(NULL) + interval;
    if (exit_time) {
        exit_time = time(NULL) + exit_time;}
    fft_buf = malloc(tunes[0].buf_len * sizeof(int16_t));
    length = 1 << tunes[0].bin_e;
    window_coefs = malloc(length * sizeof(int));
    for (i=0; i<length; i++) {
        window_coefs[i] = (int)(256*window_fn(i, length));
    }
    while (!do_exit) {
        scanner();
        time_now = time(NULL);
        if (time_now < next_tick) {
            continue;}
        // time, Hz low, Hz high, Hz step, samples, dbm, dbm, ...
        cal_time = localtime(&time_now);
        strftime(t_str, 50, "%Y-%m-%d, %H:%M:%S", cal_time);
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "==== CSV row start ====");
        for (i=0; i<tune_count; i++) {
            fprintf(file, "%s, ", t_str);
            __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG","printing timestamp");
            csv_dbm(&tunes[i]);
        }
        fflush(file);
        while (time(NULL) >= next_tick) {
            next_tick += interval;}
        if (single) {
            do_exit = 1;}
        if (exit_time && time(NULL) >= exit_time) {
            do_exit = 1;}
    }

    /* clean up */

    if (do_exit) {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "User cancel, exiting...\n");}
    else {
        __android_log_print(ANDROID_LOG_DEBUG, "RTL_LOG", "Library error %d, exiting...\n", r);}

    if (file != stdout) {
        fclose(file);}

    rtlsdr_close(dev);
    free(fft_buf);
    free(window_coefs);
    //for (i=0; i<tune_count; i++) {
    //	free(tunes[i].avg);
    //	free(tunes[i].buf8);
    //}
    return r >= 0 ? r : -r;
}

// vim: tabstop=8:softtabstop=8:shiftwidth=8:noexpandtab
