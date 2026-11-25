#include "binding-util.h"
#include "binding-types.h"
#include "bitmap.h"
#include "exception.h"
#include "graphics.h"
#include "sharedstate.h"

#include <vector>

#include <SDL.h>
#include <jni.h>

RB_METHOD(androidDeviceInfo)
{
	RB_UNUSED_PARAM;

	VALUE hash = rb_hash_new();
	JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
	jclass cls = env->FindClass("android/os/Build");

	// Build.BRAND field
	jfieldID fIDBrand = env->GetStaticFieldID(cls, "BRAND", "Ljava/lang/String;");
	jstring strJBrand = (jstring)env->GetStaticObjectField(cls, fIDBrand);
	const char *strCBrand = env->GetStringUTFChars(strJBrand, 0);

	// Build.MANUFACTURER field
	jfieldID fIDManufacturer = env->GetStaticFieldID(cls, "MANUFACTURER", "Ljava/lang/String;");
	jstring strJManufacturer = (jstring)env->GetStaticObjectField(cls, fIDManufacturer);
	const char *strCManufacturer = env->GetStringUTFChars(strJManufacturer, 0);

	// Build.MODEL field
	jfieldID fIDModel = env->GetStaticFieldID(cls, "MODEL", "Ljava/lang/String;");
	jstring strJModel = (jstring)env->GetStaticObjectField(cls, fIDModel);
	const char *strCModel = env->GetStringUTFChars(strJModel, 0);

	// Build.PRODUCT field
	jfieldID fIDProduct = env->GetStaticFieldID(cls, "PRODUCT", "Ljava/lang/String;");
	jstring strJProduct = (jstring)env->GetStaticObjectField(cls, fIDProduct);
	const char *strCProduct = env->GetStringUTFChars(strJProduct, 0);

	rb_hash_aset(hash, ID2SYM(rb_intern("brand")), rb_str_new_cstr(strCBrand));
	rb_hash_aset(hash, ID2SYM(rb_intern("manufacturer")), rb_str_new_cstr(strCManufacturer));
	rb_hash_aset(hash, ID2SYM(rb_intern("model")), rb_str_new_cstr(strCModel));
	rb_hash_aset(hash, ID2SYM(rb_intern("product")), rb_str_new_cstr(strCProduct));

	env->ReleaseStringUTFChars(strJBrand, strCBrand);
	env->ReleaseStringUTFChars(strJManufacturer, strCManufacturer);
	env->ReleaseStringUTFChars(strJModel, strCModel);
	env->ReleaseStringUTFChars(strJProduct, strCProduct);
	env->DeleteLocalRef(strJBrand);
	env->DeleteLocalRef(strJManufacturer);
	env->DeleteLocalRef(strJModel);
	env->DeleteLocalRef(strJProduct);
	env->DeleteLocalRef(cls);

	return hash;
}

RB_METHOD(androidIsTablet)
{
	RB_UNUSED_PARAM;

	return rb_bool_new(SDL_IsTablet() == SDL_TRUE);
}

RB_METHOD(androidIsTV)
{
	RB_UNUSED_PARAM;

	return rb_bool_new(SDL_IsAndroidTV() == SDL_TRUE);
}

RB_METHOD(androidIsChromebook)
{
	RB_UNUSED_PARAM;

	return rb_bool_new(SDL_IsChromebook() == SDL_TRUE);
}

RB_METHOD(androidIsDeXMode)
{
	RB_UNUSED_PARAM;

	return rb_bool_new(SDL_IsDeXMode() == SDL_TRUE);
}

RB_METHOD(androidHasVibrator)
{
	RB_UNUSED_PARAM;

	JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
	jobject activity = (jobject)SDL_AndroidGetActivity();
	jclass cls = env->GetObjectClass(activity);

	jmethodID mID = env->GetStaticMethodID(cls, "hasVibrator", "()Z");
	SDL_assert(mID != 0);
	jboolean hasVibrator = (jboolean)env->CallStaticBooleanMethod(cls, mID);

	env->DeleteLocalRef(cls);
	env->DeleteLocalRef(activity);

	return rb_bool_new(hasVibrator);
}

RB_METHOD(androidVibrate)
{
	RB_UNUSED_PARAM;

	int duration;
	rb_get_args(argc, argv, "i", &duration RB_ARG_END);

	JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
	jobject activity = (jobject)SDL_AndroidGetActivity();
	jclass cls = env->GetObjectClass(activity);

	jmethodID mID = env->GetStaticMethodID(cls, "vibrate", "(I)V");
	SDL_assert(mID != 0);
	env->CallStaticVoidMethod(cls, mID, (jint)duration);

	env->DeleteLocalRef(cls);
	env->DeleteLocalRef(activity);

	return Qnil;
}

RB_METHOD(androidVibrateStop)
{
	RB_UNUSED_PARAM;

	JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
	jobject activity = (jobject)SDL_AndroidGetActivity();
	jclass cls = env->GetObjectClass(activity);

	jmethodID mID = env->GetStaticMethodID(cls, "vibrateStop", "()V");
	SDL_assert(mID != 0);
	env->CallStaticVoidMethod(cls, mID);

	env->DeleteLocalRef(cls);
	env->DeleteLocalRef(activity);

	return Qnil;
}

RB_METHOD(androidInMultiWindow)
{
	RB_UNUSED_PARAM;

	JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
	jobject activity = (jobject)SDL_AndroidGetActivity();
	jclass cls = env->GetObjectClass(activity);

	jmethodID mID = env->GetStaticMethodID(cls, "inMultiWindow", "(Landroid/app/Activity;)Z");
	SDL_assert(mID != 0);
	jboolean winMode = (jboolean)env->CallStaticBooleanMethod(cls, mID, activity);

	env->DeleteLocalRef(cls);
	env->DeleteLocalRef(activity);

	return rb_bool_new(winMode);
}

// ---- Second-screen battle companion (ash-droid-launcher) ----
// The game talks to the activity with plain strings: JSON state out, short commands in.
// Every call is safe when the activity has no such method (older launcher): the pending
// exception is cleared and a neutral value returned.

static jmethodID companionMethod(JNIEnv *env, jclass cls, const char *name, const char *sig)
{
	jmethodID mID = env->GetStaticMethodID(cls, name, sig);
	if (!mID && env->ExceptionCheck())
		env->ExceptionClear();
	return mID;
}

RB_METHOD(androidCompanionActive)
{
	RB_UNUSED_PARAM;

	JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
	jobject activity = (jobject)SDL_AndroidGetActivity();
	jclass cls = env->GetObjectClass(activity);

	jboolean active = JNI_FALSE;
	jmethodID mID = companionMethod(env, cls, "companionActive", "()Z");
	if (mID)
		active = env->CallStaticBooleanMethod(cls, mID);
	if (env->ExceptionCheck()) {
		env->ExceptionClear();
		active = JNI_FALSE;
	}

	env->DeleteLocalRef(cls);
	env->DeleteLocalRef(activity);

	return rb_bool_new(active);
}

RB_METHOD(androidCompanionSend)
{
	RB_UNUSED_PARAM;

	VALUE str;
	rb_scan_args(argc, argv, "1", &str);
	StringValue(str);

	JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
	jobject activity = (jobject)SDL_AndroidGetActivity();
	jclass cls = env->GetObjectClass(activity);

	jmethodID mID = companionMethod(env, cls, "companionState", "([B)V");
	if (mID) {
		// Bytes, not NewStringUTF: JNI wants "modified UTF-8", which breaks on some characters
		jsize len = (jsize)RSTRING_LEN(str);
		jbyteArray arr = env->NewByteArray(len);
		if (arr) {
			env->SetByteArrayRegion(arr, 0, len, (const jbyte *)RSTRING_PTR(str));
			env->CallStaticVoidMethod(cls, mID, arr);
			env->DeleteLocalRef(arr);
		}
	}
	if (env->ExceptionCheck())
		env->ExceptionClear();

	env->DeleteLocalRef(cls);
	env->DeleteLocalRef(activity);

	return Qnil;
}

RB_METHOD(androidCompanionPoll)
{
	RB_UNUSED_PARAM;

	JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
	jobject activity = (jobject)SDL_AndroidGetActivity();
	jclass cls = env->GetObjectClass(activity);

	VALUE ret = Qnil;
	jmethodID mID = companionMethod(env, cls, "companionPoll", "()[B");
	if (mID) {
		jbyteArray arr = (jbyteArray)env->CallStaticObjectMethod(cls, mID);
		if (env->ExceptionCheck()) {
			env->ExceptionClear();
		} else if (arr) {
			jsize len = env->GetArrayLength(arr);
			jbyte *bytes = env->GetByteArrayElements(arr, 0);
			if (bytes) {
				ret = rb_utf8_str_new((const char *)bytes, len);
				env->ReleaseByteArrayElements(arr, bytes, JNI_ABORT);
			}
			env->DeleteLocalRef(arr);
		}
	}

	env->DeleteLocalRef(cls);
	env->DeleteLocalRef(activity);

	return ret;
}

// Sends the pixels of a Bitmap (a captured frame of the game) to the second screen.
// The Java array is kept between calls so a frame costs two copies and no allocation.
RB_METHOD(androidCompanionFrame)
{
	RB_UNUSED_PARAM;

	VALUE bitmapObj;
	rb_scan_args(argc, argv, "1", &bitmapObj);
	Bitmap *b = getPrivateDataCheck<Bitmap>(bitmapObj, BitmapType);
	if (!b)
		return Qfalse;

	int w = b->width(), h = b->height();
	jsize size = (jsize)(w * h * 4);
	if (size <= 0)
		return Qfalse;

	static std::vector<char> pixels;
	pixels.resize(size);
	GFX_GUARD_EXC(b->getRaw(pixels.data(), size););

	JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
	jobject activity = (jobject)SDL_AndroidGetActivity();
	jclass cls = env->GetObjectClass(activity);

	static jbyteArray frame = NULL;
	static jsize frameSize = 0;
	if (!frame || frameSize != size) {
		if (frame)
			env->DeleteGlobalRef(frame);
		frame = NULL;
		jbyteArray local = env->NewByteArray(size);
		if (local) {
			frame = (jbyteArray)env->NewGlobalRef(local);
			env->DeleteLocalRef(local);
		}
		if (env->ExceptionCheck())
			env->ExceptionClear();
		frameSize = size;
	}

	bool sent = false;
	jmethodID mID = frame ? companionMethod(env, cls, "companionFrame", "(II[B)V") : NULL;
	if (mID) {
		env->SetByteArrayRegion(frame, 0, size, (const jbyte *)pixels.data());
		env->CallStaticVoidMethod(cls, mID, (jint)w, (jint)h, frame);
		sent = true;
	}
	if (env->ExceptionCheck()) {
		env->ExceptionClear();
		sent = false;
	}

	env->DeleteLocalRef(cls);
	env->DeleteLocalRef(activity);

	return rb_bool_new(sent);
}

void androidBindingInit()
{
	VALUE module = rb_define_module("Android");

	// Android version/device info
	rb_const_set(module, rb_intern("SDK_VERSION"), INT2NUM(SDL_GetAndroidSDKVersion()));
	_rb_define_module_function(module, "device", androidDeviceInfo);
	_rb_define_module_function(module, "is_tablet?", androidIsTablet);
	_rb_define_module_function(module, "is_tv?", androidIsTV);
	_rb_define_module_function(module, "is_chromebook?", androidIsChromebook);
	_rb_define_module_function(module, "is_dex_mode?", androidIsDeXMode);

	// Vibration
	_rb_define_module_function(module, "has_vibrator?", androidHasVibrator);
	_rb_define_module_function(module, "vibrate", androidVibrate);
	_rb_define_module_function(module, "vibrate_stop", androidVibrateStop);

	// Multi-window (Android 7.0+)
	_rb_define_module_function(module, "in_multiwindow?", androidInMultiWindow);

	// Second-screen battle companion (dual-screen handhelds)
	_rb_define_module_function(module, "companion_active?", androidCompanionActive);
	_rb_define_module_function(module, "companion_send", androidCompanionSend);
	_rb_define_module_function(module, "companion_poll", androidCompanionPoll);
	_rb_define_module_function(module, "companion_frame", androidCompanionFrame);
}