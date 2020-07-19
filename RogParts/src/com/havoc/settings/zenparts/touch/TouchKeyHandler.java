/**
 * Copyright (C) 2016 The CyanogenMod project
 *               2017-2020 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.havoc.settings.rogparts.touch;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.Manifest;
import android.media.AudioManager;
import android.media.session.MediaSessionLegacyHelper;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.KeyEvent;

import com.android.internal.os.AlternativeDeviceKeyHandler;
import com.android.internal.util.havoc.Utils;
import com.android.internal.util.ArrayUtils;

import java.util.List;

public class TouchKeyHandler implements AlternativeDeviceKeyHandler {

    private static final String TAG = TouchKeyHandler.class.getSimpleName();

    private static final String GESTURE_WAKEUP_REASON = "touchscreen-gesture-wakeup";
    private static final int GESTURE_REQUEST = 0;
    private static final int GESTURE_WAKELOCK_DURATION = 3000;
    private static final int EVENT_PROCESS_WAKELOCK_DURATION = 500;
    private static final String KEY_TOUCHSCREEN_HAPTIC_FEEDBACK = "touchscreen_gesture_haptic_feedback";    

    private final Context mContext;
    private final AudioManager mAudioManager;
    private final PowerManager mPowerManager;
    private final WakeLock mGestureWakeLock;
    private final EventHandler mEventHandler;
    private final CameraManager mCameraManager;
    private final Vibrator mVibrator;

    private final SparseIntArray mActionMapping = new SparseIntArray();
    private SensorManager mSensorManager;
    private Sensor mProximitySensor;
    private WakeLock mProximityWakeLock;

    private String mRearCameraId;
    private boolean mTorchEnabled;
    private String KeyCode;

    private final BroadcastReceiver mUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int[] keycodes = intent.getIntArrayExtra(
                    Constants.UPDATE_EXTRA_KEYCODE_MAPPING);
            int[] actions = intent.getIntArrayExtra(
                    Constants.UPDATE_EXTRA_ACTION_MAPPING);
            mActionMapping.clear();
            if (keycodes != null && actions != null && keycodes.length == actions.length) {
                for (int i = 0; i < keycodes.length; i++) {
                    mActionMapping.put(keycodes[i], actions[i]);
                }
            }
        }
    };

    public TouchKeyHandler(final Context context) {
        mContext = context;

        mAudioManager = mContext.getSystemService(AudioManager.class);

        mPowerManager = context.getSystemService(PowerManager.class);
        mGestureWakeLock = mPowerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "TouchscreenGestureWakeLock");

        mEventHandler = new EventHandler();

        mCameraManager = mContext.getSystemService(CameraManager.class);
        mCameraManager.registerTorchCallback(new TorchModeCallback(), mEventHandler);

        mVibrator = context.getSystemService(Vibrator.class);

        if (mProximitySensor != null) {
            mSensorManager = context.getSystemService(SensorManager.class);
            mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            mProximityWakeLock = mPowerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "TouchscreenGestureProximityWakeLock");
        }
        mContext.registerReceiver(mUpdateReceiver,
                new IntentFilter(Constants.UPDATE_PREFS_ACTION));
    }

    private class TorchModeCallback extends CameraManager.TorchCallback {
        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (!cameraId.equals(mRearCameraId)) return;
            mTorchEnabled = enabled;
        }

        @Override
        public void onTorchModeUnavailable(String cameraId) {
            if (!cameraId.equals(mRearCameraId)) return;
            mTorchEnabled = false;
        }
    }

    public KeyEvent handleKeyEvent(final KeyEvent event) {
        final int action = mActionMapping.get(event.getScanCode(), -1);
        if (action < 0 || event.getAction() != KeyEvent.ACTION_UP || !hasSetupCompleted()) {
            Log.d(TAG, String.valueOf(event));
            if((String.valueOf(event).contains("scanCode=46")) || (String.valueOf(event).contains("scanCode=47")) || (String.valueOf(event).contains("scanCode=44"))) {
                KeyCode = "TouchScreen";
            }            
            Log.d(TAG, KeyCode);
            return event;
        }

        if (action != 0 && !mEventHandler.hasMessages(GESTURE_REQUEST)) {
            final Message msg = getMessageForAction(action);
            if (mProximitySensor != null) {
                mGestureWakeLock.acquire(2 * 100);
                mEventHandler.sendMessageDelayed(msg, 100);
                processEvent(action);
            } else {
                mGestureWakeLock.acquire(EVENT_PROCESS_WAKELOCK_DURATION);
                mEventHandler.sendMessage(msg);
            }
        }

        return null;
    }

    private boolean hasSetupCompleted() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 0) != 0;
    }

    private void processEvent(final int action) {
        mProximityWakeLock.acquire();
        mSensorManager.registerListener(new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                mProximityWakeLock.release();
                mSensorManager.unregisterListener(this);
                if (!mEventHandler.hasMessages(GESTURE_REQUEST)) {
                    // The sensor took too long; ignoring
                    return;
                }
                mEventHandler.removeMessages(GESTURE_REQUEST);
                if (event.values[0] == mProximitySensor.getMaximumRange()) {
                    Message msg = getMessageForAction(action);
                    mEventHandler.sendMessage(msg);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                // Ignore
            }

        }, mProximitySensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    private Message getMessageForAction(final int action) {
        Message msg = mEventHandler.obtainMessage(GESTURE_REQUEST);
        msg.arg1 = action;
        return msg;
    }

    private class EventHandler extends Handler {
        @Override
        public void handleMessage(final Message msg) {
            switch (msg.arg1) {
                case Constants.ACTION_BACK:
                    back();
                    break;
                case Constants.ACTION_HOME:
                    home();
                    break;
                case Constants.ACTION_RECENTS:
                    recents();
                    break;
                case Constants.ACTION_UP:
                    up();
                    break;
                case Constants.ACTION_DOWN:
                    down();
                    break;
                case Constants.ACTION_LEFT:
                    left();
                    break;
                case Constants.ACTION_RIGHT:
                    right();
                    break;
                case Constants.ACTION_ASSISTANT:
                    assistant();
                    break;
                case Constants.ACTION_WAKE_UP:
                    wakeup();
                    break;
                case Constants.ACTION_SCREENSHOT:
                    screenshot();
                    break;
                case Constants.ACTION_SCREEN_OFF:
                    screenOff();
                    break;
                case Constants.ACTION_CAMERA:
                    launchCamera();
                    break;
                case Constants.ACTION_FLASHLIGHT:
                    toggleFlashlight();
                    break;
                case Constants.ACTION_BROWSER:
                    launchBrowser();
                    break;
                case Constants.ACTION_DIALER:
                    launchDialer();
                    break;
                case Constants.ACTION_EMAIL:
                    launchEmail();
                    break;
                case Constants.ACTION_MESSAGES:
                    launchMessages();
                    break;
                case Constants.ACTION_PLAY_PAUSE_MUSIC:
                    playPauseMusic();
                    break;
                case Constants.ACTION_PREVIOUS_TRACK:
                    previousTrack();
                    break;
                case Constants.ACTION_NEXT_TRACK:
                    nextTrack();
                    break;
                case Constants.ACTION_VOLUME_DOWN:
                    volumeDown();
                    break;
                case Constants.ACTION_VOLUME_UP:
                    volumeUp();
                    break;
                case Constants.ACTION_CAMERA_MOTOR:
                    cameraMotor();
                    break;
                case Constants.ACTION_FM_RADIO:
                    fmRadio();
                    break;
            }
        }
    }

    private void back() {
        Utils.sendKeycode(KeyEvent.KEYCODE_BACK);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void home() {
        Utils.sendKeycode(KeyEvent.KEYCODE_HOME);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void recents() {
        Utils.sendKeycode(KeyEvent.KEYCODE_APP_SWITCH);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void up() {
        Utils.sendKeycode(KeyEvent.KEYCODE_DPAD_UP);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void down() {
        Utils.sendKeycode(KeyEvent.KEYCODE_DPAD_DOWN);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void left() {
        Utils.sendKeycode(KeyEvent.KEYCODE_DPAD_LEFT);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void right() {
        Utils.sendKeycode(KeyEvent.KEYCODE_DPAD_RIGHT);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void assistant() {
        Utils.sendKeycode(KeyEvent.KEYCODE_ASSIST);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void wakeup() {
        Utils.sendKeycode(KeyEvent.KEYCODE_WAKEUP);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void screenshot() {
        Utils.takeScreenshot(true);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void screenOff() {
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        pm.goToSleep(SystemClock.uptimeMillis());
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void launchCamera() {
        mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
        final Intent intent = new Intent(android.content.Intent.ACTION_SCREEN_CAMERA_GESTURE);
        mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT,
                Manifest.permission.STATUS_BAR_SERVICE);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void launchBrowser() {
        mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
        mPowerManager.wakeUp(SystemClock.uptimeMillis(), GESTURE_WAKEUP_REASON);
        final Intent intent = getLaunchableIntent(
                new Intent(Intent.ACTION_VIEW, Uri.parse("http:")));
        startActivitySafely(intent);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void launchDialer() {
        mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
        mPowerManager.wakeUp(SystemClock.uptimeMillis(), GESTURE_WAKEUP_REASON);
        final Intent intent = new Intent(Intent.ACTION_DIAL, null);
        startActivitySafely(intent);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void launchEmail() {
        mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
        mPowerManager.wakeUp(SystemClock.uptimeMillis(), GESTURE_WAKEUP_REASON);
        final Intent intent = getLaunchableIntent(
                new Intent(Intent.ACTION_VIEW, Uri.parse("mailto:")));
        startActivitySafely(intent);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void launchMessages() {
        mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
        mPowerManager.wakeUp(SystemClock.uptimeMillis(), GESTURE_WAKEUP_REASON);
        final Intent intent = getLaunchableIntent(
                new Intent(Intent.ACTION_VIEW, Uri.parse("sms:")));
        startActivitySafely(intent);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void toggleFlashlight() {
        String rearCameraId = getRearCameraId();
        if (rearCameraId != null) {
            mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
            try {
                mCameraManager.setTorchMode(rearCameraId, !mTorchEnabled);
                mTorchEnabled = !mTorchEnabled;
            } catch (CameraAccessException e) {
                // Ignore
            }
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
        }
    }

    private void playPauseMusic() {
        dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void previousTrack() {
        dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void nextTrack() {
        dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_NEXT);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void volumeDown() {
        mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void volumeUp() {
        mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void cameraMotor() {
        mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
        mPowerManager.wakeUp(SystemClock.uptimeMillis(), GESTURE_WAKEUP_REASON);
        Intent intent = new Intent("com.asus.motorservice.action.WIDGET_BTN_CLICKED");
        intent.setPackage("com.asus.motorservice");
        mContext.sendBroadcast(intent);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void fmRadio() {
        mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
        mPowerManager.wakeUp(SystemClock.uptimeMillis(), GESTURE_WAKEUP_REASON);
        Intent LaunchIntent = mContext.getPackageManager().getLaunchIntentForPackage("com.asus.fmradio");
        mContext.startActivity(LaunchIntent);
        if (KeyCode == "TouchScreen") {
            doTouchScreenHapticFeedback();
        }
    }

    private void dispatchMediaKeyWithWakeLockToMediaSession(final int keycode) {
        final MediaSessionLegacyHelper helper = MediaSessionLegacyHelper.getHelper(mContext);
        if (helper == null) {
            Log.w(TAG, "Unable to send media key event");
            return;
        }
        KeyEvent event = new KeyEvent(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keycode, 0);
        helper.sendMediaButtonEvent(event, true);
        event = KeyEvent.changeAction(event, KeyEvent.ACTION_UP);
        helper.sendMediaButtonEvent(event, true);
    }

    private void startActivitySafely(final Intent intent) {
        if (intent == null) {
            Log.w(TAG, "No intent passed to startActivitySafely");
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            final UserHandle user = new UserHandle(UserHandle.USER_CURRENT);
            mContext.startActivityAsUser(intent, null, user);
        } catch (ActivityNotFoundException e) {
            // Ignore
        }
    }

    private void doTouchScreenHapticFeedback() {
        if (mVibrator == null) {
            return;
        }

        final boolean enabled = Settings.System.getInt(mContext.getContentResolver(),
                KEY_TOUCHSCREEN_HAPTIC_FEEDBACK, 1) != 0;
        if (enabled) {
            mVibrator.vibrate(50);
        }
    }

    private String getRearCameraId() {
        if (mRearCameraId == null) {
            try {
                for (final String cameraId : mCameraManager.getCameraIdList()) {
                    final CameraCharacteristics characteristics =
                            mCameraManager.getCameraCharacteristics(cameraId);
                    final int orientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (orientation == CameraCharacteristics.LENS_FACING_BACK) {
                        mRearCameraId = cameraId;
                        break;
                    }
                }
            } catch (CameraAccessException e) {
                // Ignore
            }
        }
        return mRearCameraId;
    }

    private Intent getLaunchableIntent(Intent intent) {
        PackageManager pm = mContext.getPackageManager();
        List<ResolveInfo> resInfo = pm.queryIntentActivities(intent, 0);
        if (resInfo.isEmpty()) {
            return null;
        }
        return pm.getLaunchIntentForPackage(resInfo.get(0).activityInfo.packageName);
    }

    @Override
    public boolean isDisabledKeyEvent(KeyEvent event) {
        return false;
    }

    @Override
    public boolean isWakeEvent(KeyEvent event){
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }
        return event.getScanCode() == Constants.GESTURE_DOUBLE_CLICK;
    }

    @Override
    public boolean isCameraLaunchEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }
        return event.getScanCode() == Constants.GESTURE_C;
   }

    @Override
    public boolean canHandleKeyEvent(KeyEvent event) {
        return ArrayUtils.contains(Constants.sSupportedKeycodes, event.getScanCode());
    }
    
    @Override
    public Intent isActivityLaunchEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return null;
        }
        return null;
    }
}
