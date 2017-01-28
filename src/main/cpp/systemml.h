/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
 
/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class org_apache_sysml_utils_NativeHelper */

#ifndef _Included_org_apache_sysml_utils_NativeHelper
#define _Included_org_apache_sysml_utils_NativeHelper
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     org_apache_sysml_utils_NativeHelper
 * Method:    matrixMultDenseDense
 * Signature: ([D[D[DIIII)Z
 */
JNIEXPORT jboolean JNICALL Java_org_apache_sysml_utils_NativeHelper_matrixMultDenseDense
  (JNIEnv *, jclass, jdoubleArray, jdoubleArray, jdoubleArray, jint, jint, jint, jint);

/*
 * Class:     org_apache_sysml_utils_NativeHelper
 * Method:    tsmm
 * Signature: ([D[DIIZI)Z
 */
JNIEXPORT jboolean JNICALL Java_org_apache_sysml_utils_NativeHelper_tsmm
  (JNIEnv *, jclass, jdoubleArray, jdoubleArray, jint, jint, jboolean, jint);

/*
 * Class:     org_apache_sysml_utils_NativeHelper
 * Method:    conv2dDense
 * Signature: ([D[D[DIIIIIIIIIIIIII)Z
 */
JNIEXPORT jboolean JNICALL Java_org_apache_sysml_utils_NativeHelper_conv2dDense
  (JNIEnv *, jclass, jdoubleArray, jdoubleArray, jdoubleArray, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint);

/*
 * Class:     org_apache_sysml_utils_NativeHelper
 * Method:    conv2dBiasAddDense
 * Signature: ([D[D[D[DIIIIIIIIIIIIII)Z
 */
JNIEXPORT jboolean JNICALL Java_org_apache_sysml_utils_NativeHelper_conv2dBiasAddDense
  (JNIEnv *, jclass, jdoubleArray, jdoubleArray, jdoubleArray, jdoubleArray, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint);

/*
 * Class:     org_apache_sysml_utils_NativeHelper
 * Method:    conv2dBackwardDataDense
 * Signature: ([D[D[DIIIIIIIIIIIIII)Z
 */
JNIEXPORT jboolean JNICALL Java_org_apache_sysml_utils_NativeHelper_conv2dBackwardDataDense
  (JNIEnv *, jclass, jdoubleArray, jdoubleArray, jdoubleArray, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint);

/*
 * Class:     org_apache_sysml_utils_NativeHelper
 * Method:    conv2dBackwardFilterDense
 * Signature: ([D[D[DIIIIIIIIIIIIII)Z
 */
JNIEXPORT jboolean JNICALL Java_org_apache_sysml_utils_NativeHelper_conv2dBackwardFilterDense
  (JNIEnv *, jclass, jdoubleArray, jdoubleArray, jdoubleArray, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint, jint);

#ifdef __cplusplus
}
#endif
#endif

 