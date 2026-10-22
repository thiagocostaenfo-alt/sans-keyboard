/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

import helium314.keyboard.event.HapticEvent;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.settings.Defaults;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.utils.KtxKt;
import helium314.keyboard.latin.settings.SettingsValues;

/**
 * This class gathers audio feedback and haptic feedback functions.
 * <p>
 * It offers a consistent and simple interface that allows LatinIME to forget about the
 * complexity of settings and the like.
 */
public final class AudioAndHapticFeedbackManager {
    private AudioManager mAudioManager;
    private Vibrator mVibrator;

    private SettingsValues mSettingsValues;
    private boolean mSoundOn;
    private boolean mDoNotDisturb;

    private static final AudioAndHapticFeedbackManager sInstance =
            new AudioAndHapticFeedbackManager();

    public static AudioAndHapticFeedbackManager getInstance() {
        return sInstance;
    }

    private AudioAndHapticFeedbackManager() {
        // Intentional empty constructor for singleton.
    }

    public static void init(final Context context) {
        sInstance.initInternal(context);
    }

    private void initInternal(final Context context) {
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mContext = context.getApplicationContext();
    }

    // Sans Keyboard: physical keyboard sounds played with a SoundPool
    private Context mContext;
    private SoundPool mSoundPool;
    private String mLoadedPack;
    private int[] mKeySounds = new int[0];
    private int mSpaceSound, mEnterSound, mDeleteSound;
    private final java.util.Random mRandom = new java.util.Random();

    private String soundPack() {
        if (mContext == null) return "system";
        return KtxKt.prefs(mContext).getString(Settings.PREF_SANS_KEY_SOUND, Defaults.PREF_SANS_KEY_SOUND);
    }

    private void loadPack(final String pack) {
        if (pack.equals(mLoadedPack)) return;
        if (mSoundPool != null) { mSoundPool.release(); mSoundPool = null; }
        mLoadedPack = pack;
        if ("system".equals(pack) || mContext == null) return;
        mSoundPool = new SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        // key1, key2, key3, space, enter, delete
        final int[] res = switch (pack) {
            case "creamy" -> new int[] { R.raw.sans_creamy_key1, R.raw.sans_creamy_key2, R.raw.sans_creamy_key3,
                    R.raw.sans_creamy_space, R.raw.sans_creamy_enter, R.raw.sans_creamy_delete };
            case "clacky" -> new int[] { R.raw.sans_clacky_key1, R.raw.sans_clacky_key2, R.raw.sans_clacky_key3,
                    R.raw.sans_clacky_space, R.raw.sans_clacky_enter, R.raw.sans_clacky_delete };
            case "mx_blue" -> new int[] { R.raw.sans_mx_blue_key1, R.raw.sans_mx_blue_key2, R.raw.sans_mx_blue_key3,
                    R.raw.sans_mx_blue_space, R.raw.sans_mx_blue_enter, R.raw.sans_mx_blue_delete };
            case "mx_brown" -> new int[] { R.raw.sans_mx_brown_key1, R.raw.sans_mx_brown_key2, R.raw.sans_mx_brown_key3,
                    R.raw.sans_mx_brown_space, R.raw.sans_mx_brown_enter, R.raw.sans_mx_brown_delete };
            case "mx_red" -> new int[] { R.raw.sans_mx_red_key1, R.raw.sans_mx_red_key2, R.raw.sans_mx_red_key3,
                    R.raw.sans_mx_red_space, R.raw.sans_mx_red_enter, R.raw.sans_mx_red_delete };
            case "holy_panda" -> new int[] { R.raw.sans_holy_panda_key1, R.raw.sans_holy_panda_key2, R.raw.sans_holy_panda_key3,
                    R.raw.sans_holy_panda_space, R.raw.sans_holy_panda_enter, R.raw.sans_holy_panda_delete };
            case "alpaca" -> new int[] { R.raw.sans_alpaca_key1, R.raw.sans_alpaca_key2, R.raw.sans_alpaca_key3,
                    R.raw.sans_alpaca_space, R.raw.sans_alpaca_enter, R.raw.sans_alpaca_delete };
            case "topre" -> new int[] { R.raw.sans_topre_key1, R.raw.sans_topre_key2, R.raw.sans_topre_key3,
                    R.raw.sans_topre_space, R.raw.sans_topre_enter, R.raw.sans_topre_delete };
            case "typewriter" -> new int[] { R.raw.sans_typewriter_key1, R.raw.sans_typewriter_key2, R.raw.sans_typewriter_key3,
                    R.raw.sans_typewriter_space, R.raw.sans_typewriter_enter, R.raw.sans_typewriter_delete };
            case "ibm" -> new int[] { R.raw.sans_ibm_key1, R.raw.sans_ibm_key2, R.raw.sans_ibm_key3,
                    R.raw.sans_ibm_space, R.raw.sans_ibm_enter, R.raw.sans_ibm_delete };
            case "alps" -> new int[] { R.raw.sans_alps_key1, R.raw.sans_alps_key2, R.raw.sans_alps_key3,
                    R.raw.sans_alps_space, R.raw.sans_alps_enter, R.raw.sans_alps_delete };
            default -> new int[] { R.raw.sans_thocky_key1, R.raw.sans_thocky_key2, R.raw.sans_thocky_key3,
                    R.raw.sans_thocky_space, R.raw.sans_thocky_enter, R.raw.sans_thocky_delete };
        };
        mKeySounds = new int[] {
                mSoundPool.load(mContext, res[0], 1),
                mSoundPool.load(mContext, res[1], 1),
                mSoundPool.load(mContext, res[2], 1),
        };
        mSpaceSound = mSoundPool.load(mContext, res[3], 1);
        mEnterSound = mSoundPool.load(mContext, res[4], 1);
        mDeleteSound = mSoundPool.load(mContext, res[5], 1);
    }

    /** returns false if the system sound should be used instead */
    private boolean playPackSound(final int code) {
        final String pack = soundPack();
        loadPack(pack);
        if (mSoundPool == null) return false;
        final int id = switch (code) {
            case KeyCode.DELETE -> mDeleteSound;
            case Constants.CODE_ENTER -> mEnterSound;
            case Constants.CODE_SPACE -> mSpaceSound;
            default -> mKeySounds[mRandom.nextInt(mKeySounds.length)];
        };
        final float setVolume = mSettingsValues.mKeypressSoundVolume;
        final float volume = setVolume < 0 ? 0.6f : setVolume;
        // a slightly different pitch every time, so fast typing does not sound like a machine gun
        final float rate = 0.96f + mRandom.nextFloat() * 0.08f;
        mSoundPool.play(id, volume, volume, 1, 0, rate);
        return true;
    }

    public void performHapticAndAudioFeedback(
        final int code,
        final View viewToPerformHapticFeedbackOn,
        final HapticEvent hapticEvent
    ) {
        performHapticFeedback(viewToPerformHapticFeedbackOn, hapticEvent);
        performAudioFeedback(code, hapticEvent);
    }

    public boolean hasVibrator() {
        return mVibrator != null && mVibrator.hasVibrator();
    }

    public void vibrate(final long milliseconds) {
        if (mVibrator == null || milliseconds <= 0) {
            return;
        }
        mVibrator.vibrate(milliseconds);
    }

    private boolean reevaluateIfSoundIsOn() {
        if (mSettingsValues == null || !mSettingsValues.mSoundOn || mAudioManager == null || mDoNotDisturb) {
            return false;
        }
        return mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
    }

    public void performAudioFeedback(final int code, final HapticEvent hapticEvent) {
        // if mAudioManager is null, we can't play a sound anyway, so return
        if (mAudioManager == null) {
            return;
        }
        if (!mSoundOn) {
            return;
        }
        if (hapticEvent != HapticEvent.KEY_PRESS) {
            return;
        }
        if (playPackSound(code)) return;
        final int sound = switch (code) {
            case KeyCode.DELETE -> AudioManager.FX_KEYPRESS_DELETE;
            case Constants.CODE_ENTER -> AudioManager.FX_KEYPRESS_RETURN;
            case Constants.CODE_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR;
            default -> AudioManager.FX_KEYPRESS_STANDARD;
        };
        mAudioManager.playSoundEffect(sound, mSettingsValues.mKeypressSoundVolume);
    }

    public void performHapticFeedback(final View viewToPerformHapticFeedbackOn, final HapticEvent hapticEvent) {
        if (!mSettingsValues.mVibrateOn || (mDoNotDisturb && !mSettingsValues.mVibrateInDndMode)) {
            return;
        }
        if (hapticEvent == HapticEvent.NO_HAPTICS) {
            // Avoid surprises with the handling of HapticFeedbackConstants.NO_HAPTICS
            return;
        }
        if (hapticEvent.allowCustomDuration && mSettingsValues.mKeypressVibrationDuration >= 0) {
            vibrate(mSettingsValues.mKeypressVibrationDuration);
            return;
        }
        // Go ahead with the system default
        if (viewToPerformHapticFeedbackOn != null) {
            viewToPerformHapticFeedbackOn.performHapticFeedback(
                    hapticEvent.feedbackConstant,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }

    public void onSettingsChanged(final SettingsValues settingsValues) {
        mSettingsValues = settingsValues;
        mSoundOn = reevaluateIfSoundIsOn();
    }

    public void onRingerModeChanged(boolean doNotDisturb) {
        mDoNotDisturb = doNotDisturb;
        mSoundOn = reevaluateIfSoundIsOn();
    }
}
