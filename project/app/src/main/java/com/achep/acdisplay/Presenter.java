/*
 * Copyright (C) 2014 AChep@xda <artemchep@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package com.achep.acdisplay;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.achep.acdisplay.blacklist.Blacklist;
import com.achep.acdisplay.notifications.NotificationPresenter;
import com.achep.acdisplay.notifications.OpenNotification;
import com.achep.acdisplay.services.activemode.sensors.ProximitySensor;
import com.achep.acdisplay.ui.activities.AcDisplayActivity;
import com.achep.acdisplay.ui.activities.KeyguardActivity;
import com.achep.base.utils.power.PowerUtils;
import com.achep.base.utils.zen.ZenConsts;
import com.achep.base.utils.zen.ZenUtils;

import static com.achep.base.Build.DEBUG;

/**
 * Created by Artem on 07.03.14.
 */
public class Presenter {

    private static final String TAG = "AcDisplayPresenter";
    private static final String WAKE_LOCK_TAG = "AcDisplay launcher.";

    private static Presenter sPresenter;

    @Nullable
    private AcDisplayActivity mActivity;

    public static synchronized Presenter getInstance() {
        if (sPresenter == null) {
            sPresenter = new Presenter();
        }
        return sPresenter;
    }

    public void attachActivity(@Nullable AcDisplayActivity activity) {
        mActivity = activity;
    }

    public void detachActivity() {
        attachActivity(null);
    }

    public void kill() {
        if (mActivity != null) mActivity.finish();
    }

    //-- START-UP -------------------------------------------------------------

    public boolean tryStartGuiCauseNotification(@NonNull Context context,
                                                @NonNull OpenNotification n) {
        NotificationPresenter np = NotificationPresenter.getInstance();
        if (!np.isTestNotification(context, n)) { // force test notification to be shown
            Config config = Config.getInstance();
            if (!config.isEnabled() || !config.isNotifyWakingUp()
                    // Inactive time
                    || config.isInactiveTimeEnabled()
                    && InactiveTimeHelper.isInactiveTime(config)
                    // Only while charging
                    || config.isEnabledOnlyWhileCharging()
                    && !PowerUtils.isPlugged(context)) {
                // Don't turn screen on due to user settings.
                return false;
            }

            // Respect the device's zen mode.
            final int zenMode = ZenUtils.getValue(context);
            if (DEBUG) Log.d(TAG, "The current ZEN mode is " + ZenUtils.zenModeToString(zenMode));
            switch (zenMode) {
                case ZenConsts.ZEN_MODE_IMPORTANT_INTERRUPTIONS:
                    if (n.getNotification().priority >= Notification.PRIORITY_HIGH) {
                        break;
                    }
                case ZenConsts.ZEN_MODE_NO_INTERRUPTIONS:
                    return false;
            }

            if (ProximitySensor.isNear()) {
                // Don't display while device is face down.
                return false;
            }

            String packageName = n.getPackageName();
            Blacklist blacklist = Blacklist.getInstance();
            if (blacklist.getAppConfig(packageName).isRestricted()) {
                // Don't display due to app settings.
                return false;
            }
        }

        return tryStartGuiCauseSensor(context);
    }

    public boolean tryStartGuiCauseSensor(@NonNull Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        TelephonyManager ts = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (PowerUtils.isScreenOn(pm) || ts.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
            // Screen is on || phone call.
            return false;
        }

        // Wake up from possible deep sleep.
        //
        //           )))
        //          (((
        //        +-----+
        //        |     |]
        //        `-----'    Good morning! ^-^
        //      ___________
        //      `---------'
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).acquire(1000);

        kill();
        context.startActivity(new Intent(context, AcDisplayActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_NO_USER_ACTION
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_FROM_BACKGROUND)
                .putExtra(KeyguardActivity.EXTRA_TURN_SCREEN_ON, true));

        Log.i(TAG, "Launching AcDisplay activity.");
        return true;
    }

    public boolean tryStartGuiCauseKeyguard(@NonNull Context context) {
        context.startActivity(new Intent(context, AcDisplayActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        return true;
    }

}
