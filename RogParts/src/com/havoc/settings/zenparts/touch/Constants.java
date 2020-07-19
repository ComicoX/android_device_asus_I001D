/*
 * Copyright (C) 2015 The CyanogenMod Project
 * Copyright (C) 2017-2020 The LineageOS Project
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

import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserHandle;
import android.provider.Settings;
import androidx.preference.SwitchPreference;
import androidx.preference.PreferenceManager;

import android.media.AudioManager;

public class Constants {

    // Broadcast action for settings update
    static final String UPDATE_PREFS_ACTION = "com.havoc.settings.rogparts.touch.UPDATE_SETTINGS";

    // Screen off Gesture actions
    static final int ACTION_BACK = 2;
    static final int ACTION_HOME = 3;
    static final int ACTION_RECENTS = 4;
    static final int ACTION_UP = 5;
    static final int ACTION_DOWN = 6;
    static final int ACTION_LEFT = 7;
    static final int ACTION_RIGHT = 8;
    static final int ACTION_ASSISTANT = 9;
    static final int ACTION_WAKE_UP = 10;
    static final int ACTION_SCREENSHOT = 11;
    static final int ACTION_SCREEN_OFF = 12;
    static final int ACTION_FLASHLIGHT = 13;
    static final int ACTION_CAMERA = 14;
    static final int ACTION_BROWSER = 15;
    static final int ACTION_DIALER = 16;
    static final int ACTION_EMAIL = 17;
    static final int ACTION_MESSAGES = 18;
    static final int ACTION_PLAY_PAUSE_MUSIC = 19;
    static final int ACTION_PREVIOUS_TRACK = 20;
    static final int ACTION_NEXT_TRACK = 21;
    static final int ACTION_VOLUME_DOWN = 22;
    static final int ACTION_VOLUME_UP = 23;
    static final int ACTION_CAMERA_MOTOR = 24;
    static final int ACTION_FM_RADIO = 25;

    // Broadcast extra: keycode mapping (int[]: key = gesture ID, value = keycode)
    static final String UPDATE_EXTRA_KEYCODE_MAPPING = "keycode_mappings";
    // Broadcast extra: assigned actions (int[]: key = gesture ID, value = action)
    static final String UPDATE_EXTRA_ACTION_MAPPING = "action_mappings";

    static final int GESTURE_DOUBLE_CLICK = 116;
    static final int GESTURE_SWIPE_UP = 103;
    static final int GESTURE_W = 17;
    static final int GESTURE_S = 31;
    static final int GESTURE_E = 18;
    static final int GESTURE_C = 46;
    static final int GESTURE_Z = 44;
    static final int GESTURE_V = 47;

    static final int[] sSupportedKeycodes = new int[]
    {
        GESTURE_DOUBLE_CLICK,
        GESTURE_SWIPE_UP,
        GESTURE_W,
        GESTURE_S,
        GESTURE_E,
        GESTURE_C,
        GESTURE_Z,
        GESTURE_V
    };
}
