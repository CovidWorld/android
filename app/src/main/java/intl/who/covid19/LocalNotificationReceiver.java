package intl.who.covid19;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.res.ResourcesCompat;

import java.util.Calendar;

import intl.who.covid19.ui.WelcomeActivity;

public class LocalNotificationReceiver extends BroadcastReceiver {
    private static final int ID_QUARANTINE = 1;

    public static void scheduleNotification(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        if (App.get(context).isInQuarantine()) {
            Calendar cal = Calendar.getInstance();
            if (cal.get(Calendar.HOUR_OF_DAY) >= 9) {
                cal.add(Calendar.DATE, 1);
            }
            cal.set(Calendar.HOUR_OF_DAY, 9);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            App.log("Scheduling alarm local notification for " + cal.getTime());
            if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), getPendingIntent(context));
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), getPendingIntent(context));
            }
        } else {
            App.log("Canceling alarm local notification");
            alarmManager.cancel(getPendingIntent(context));
        }
    }

    private static PendingIntent getPendingIntent(Context context) {
        return PendingIntent.getBroadcast(context, ID_QUARANTINE, new Intent(context, LocalNotificationReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!App.get(context).isInQuarantine()) {
            return;
        }
        // Schedule notification for next day
        scheduleNotification(context);
        // Show the notification
        String text = context.getString(R.string.notification_quarantineInfo_text, App.get(context).getDaysLeftInQuarantine());
        Notification notification = new NotificationCompat.Builder(context, App.NOTIFICATION_CHANNEL_ALARM)
                .setColor(ResourcesCompat.getColor(context.getResources(), R.color.red, null))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_quarantineInfo_title))
                .setContentText(text)
                .setContentIntent(PendingIntent.getActivity(context, 1, new Intent(context, WelcomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        NotificationManagerCompat.from(context).notify(App.NOTIFICATION_ID_QUARANTINE_INFO, notification);
    }
}
