package me.calebjones.spacelaunchnow.content.services;


import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.calebjones.spacelaunchnow.BuildConfig;
import me.calebjones.spacelaunchnow.MainActivity;
import me.calebjones.spacelaunchnow.R;
import me.calebjones.spacelaunchnow.content.database.ListPreferences;
import me.calebjones.spacelaunchnow.content.database.SwitchPreferences;
import me.calebjones.spacelaunchnow.content.models.Launch;
import me.calebjones.spacelaunchnow.content.models.Strings;
import me.calebjones.spacelaunchnow.utils.Utils;
import timber.log.Timber;


public class NextLaunchTracker extends IntentService implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private Launch nextLaunch;
    private Launch storedLaunch;
    private SharedPreferences sharedPref;
    private ListPreferences sharedPreference;
    private SwitchPreferences switchPreferences;
    public static List<Launch> upcomingLaunchList;
    private Calendar rightNow;
    private AlarmManager alarmManager;
    private long interval;
    private static final String NAME_KEY = "me.calebjones.spacelaunchnow.wear.nextname";
    private static final String TIME_KEY = "me.calebjones.spacelaunchnow.wear.nexttime";

    private GoogleApiClient mGoogleApiClient;

    public NextLaunchTracker() {
        super("NextLaunchTracker");
    }

    public void onCreate() {
        Timber.d("NextLaunchTracker - onCreate");
        rightNow = Calendar.getInstance();
        super.onCreate();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
    }


    public int onStartCommand(Intent intent, int flags, int startId) {

        this.sharedPreference = ListPreferences.getInstance(getApplicationContext());
        this.switchPreferences = SwitchPreferences.getInstance(getApplicationContext());
        this.sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        upcomingLaunchList = this.sharedPreference.getNextLaunches();

        mGoogleApiClient.connect();
        Timber.d("mGoogleApiClient - connect");

        if (upcomingLaunchList != null && upcomingLaunchList.size() > 0) {
            checkNextLaunch();
        } else {
            interval = 3600000;
            scheduleUpdate();
        }
    }

    private void checkNextLaunch() {
        upcomingLaunchList = this.sharedPreference.getNextLaunches();

        if (upcomingLaunchList != null && upcomingLaunchList.size() > 0) {
//            for (int i = 0; i < upcomingLaunchList.size(); i++) {
//                if (upcomingLaunchList.get(i).getStatus() == 1) {
//                    nextLaunch = upcomingLaunchList.get(i);
//                    break;
//                }
//            }
            nextLaunch = upcomingLaunchList.get(0);
            storedLaunch = sharedPreference.getNextLaunch();
        }

        //Check if the stored launch is still the next launch.
        if (storedLaunch != null && nextLaunch != null) {

            //If they do not match this means nextLaunch has changed IE a launch executed.
            if (nextLaunch.getId().intValue() != storedLaunch.getId().intValue()) {
                this.sharedPreference.setNextLaunch(nextLaunch);
                this.switchPreferences.setPrevFiltered(false);

                Intent updatePreviousLaunches = new Intent(this, LaunchDataService.class);
                updatePreviousLaunches.setAction(Strings.ACTION_GET_PREV_LAUNCHES);
                updatePreviousLaunches.putExtra("URL", Utils.getBaseURL());
                startService(updatePreviousLaunches);

                checkStatus(nextLaunch);

                //They do match, check if the launch time has moved.
            } else {

                if (Math.abs(nextLaunch.getNetstamp() - storedLaunch.getNetstamp())  > 60){

                    sharedPreference.setNextLaunch(nextLaunch);
                    upcomingLaunchList.set(0, nextLaunch);
                    sharedPreference.setNextLaunches(upcomingLaunchList);
                    checkStatus(nextLaunch);

                    Intent broadcastIntent = new Intent();
                    broadcastIntent.setAction(Strings.ACTION_SUCCESS_UP_LAUNCHES);
                    this.getApplicationContext().sendBroadcast(broadcastIntent);

                } else {
                    if (storedLaunch.getIsNotifiedDay()) {
                        nextLaunch.setIsNotifiedDay(true);
                    }
                    if (storedLaunch.getIsNotifiedHour()) {
                        nextLaunch.setIsNotifiedhour(true);
                    }
                    if (storedLaunch.isFavorite()) {
                        nextLaunch.isFavorite();
                    }

                    sharedPreference.setNextLaunch(nextLaunch);
                    upcomingLaunchList.set(0, nextLaunch);
                    sharedPreference.setNextLaunches(upcomingLaunchList);
                    checkStatus(nextLaunch);
                }
            }
        } else if (nextLaunch != null){
            this.sharedPreference.setNextLaunch(nextLaunch);
            checkStatus(nextLaunch);
        }
    }

    //TODO THIS IS BUGGED
    private void checkStatus(Launch launch) {
        if (launch != null && launch.getNetstamp() > 0) {

            long longdate = launch.getNetstamp();
            longdate = longdate * 1000;
            final Date date = new Date(longdate);

            Calendar future = DateToCalendar(date);
            Calendar now = rightNow;

            now.setTimeInMillis(System.currentTimeMillis());
            long timeToFinish = future.getTimeInMillis() - now.getTimeInMillis();
            boolean notify = this.sharedPref.getBoolean("notifications_new_message", true);

            //Launch is in less then one hour
            if (timeToFinish > 0) {
                if (timeToFinish < 3600000) {
                    if (notify) {
                        int minutes = (int) ((timeToFinish / (1000 * 60)) % 60);
                        //Check settings to see if user should be notified.
                        if (this.sharedPref.getBoolean("notifications_launch_imminent", true)) {
                            if (!launch.getIsNotifiedHour()) {
                                notifyUserImminent(launch, minutes);
                                launch.setIsNotifiedhour(true);
                                this.sharedPreference.setNextLaunch(launch);
                            }
                            //If its a saved launch check notification
                        } else if (launch.isFavorite() && this.sharedPref.getBoolean("notifications_launch_imminent_saved", true)) {
                            if (!launch.getIsNotifiedHour()) {
                                notifyUserImminent(launch, minutes);
                                launch.setIsNotifiedhour(true);
                                this.sharedPreference.setNextLaunch(launch);
                            }
                        }
                    }
                    interval = 3600000;
                    scheduleUpdate();

                    //Launch is in less then 24 hours
                } else if (timeToFinish < 86400000) {
                    Timber.v("Less than 24 hours.");
                    if (notify) {
                        int hours = (int) ((timeToFinish / (1000 * 60 * 60)) % 24);
                        //Check settings to see if user should be notified.
                        if (this.sharedPref.getBoolean("notifications_launch_day", false)) {
                            if (!launch.getIsNotifiedDay()) {
                                notifyUser(launch, hours);
                                launch.setIsNotifiedDay(true);
                                this.sharedPreference.setNextLaunch(launch);
                            }
                            //If its a saved launch check notification
                        } else if (launch.isFavorite() && this.sharedPref.getBoolean("notifications_launch_day_saved", true)) {
                            if (!launch.getIsNotifiedDay()) {
                                notifyUser(launch, hours);
                                launch.setIsNotifiedDay(true);
                                this.sharedPreference.setNextLaunch(launch);
                            }
                        }
                    }
                    interval = timeToFinish / 2;
                    if (interval < 3600000) {
                        interval = 3500000;
                    }
                    scheduleUpdate();
                    //Launch is within 48 hours
                } else if (timeToFinish < 172800000){
                    interval = ((future.getTimeInMillis() - 82800000) - now.getTimeInMillis());
                    scheduleUpdate();
                } else {
                    interval = (timeToFinish / 2) + 43200000;
                    scheduleUpdate();
                }
            } else {
                interval = 3600000;
                scheduleUpdate();
            }
        } else {
            interval = (86400000);
            scheduleUpdate();
        }
    }

    private void notifyUserImminent(Launch launch, int minutes) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());

        String launchDate;
        String expandedText;
        String launchName = launch.getName();
        String launchURL = launch.getVidURL();
        String launchPad = launch.getLocation().getName();

        if (launch.getMissions().size() > 0) {
            expandedText = "Launch attempt in " + minutes + " minutes from " + launchPad + ". \n\n" + launch.getMissions().get(0).getDescription();
        } else {
            expandedText = "Launch attempt in " + minutes + " minutes from " + launchPad;
        }

        //Get launch date
        if (sharedPref.getBoolean("local_time", true)) {
            SimpleDateFormat df = new SimpleDateFormat("EEEE, MMM dd yyyy hh:mm a zzz");
            df.toLocalizedPattern();
            Date date = new Date(launch.getWindowstart());
            launchDate = df.format(date);
        } else {
            launchDate = launch.getWindowstart();
        }

        Intent mainActivityIntent = new Intent(this, MainActivity.class);
        mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent appIntent = PendingIntent.getActivity(this, 0, mainActivityIntent, 0);

        // Sets up the Open and Share action buttons that will appear in the
        // big view of the notification.
        Intent vidIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(launchURL));
        PendingIntent vidPendingIntent = PendingIntent.getActivity(this, 0, vidIntent, 0);

        Intent shareLaunch = Utils.buildIntent(launch);
        PendingIntent sharePendingIntent = PendingIntent.getActivity(this, 0, shareLaunch, 0);

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        //TODO add launch image when ready from LL
        NotificationCompat.WearableExtender wearableExtender =
                new NotificationCompat.WearableExtender()
                        .setHintHideIcon(true)
                        .setBackground(BitmapFactory.decodeResource(this.getResources(),
                                R.drawable.nav_header));

        mBuilder.setContentTitle(launchName)
                .setContentText("Launch attempt in " + minutes + " minutes from " + launchPad)
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(expandedText)
                        .setSummaryText(launchDate))
                .extend(wearableExtender)
                .setSound(alarmSound)
                .addAction(R.drawable.ic_open_in_browser_white, "Watch Live", vidPendingIntent)
                .addAction(R.drawable.ic_menu_share_white, "Share", sharePendingIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && sharedPref.getBoolean("notifications_new_message_vibrate", true)) {
            mBuilder.setPriority(Notification.PRIORITY_HIGH)
                    .setVibrate(new long[]{1000, 1000})
                    .setLights(Color.RED, 3000, 3000);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN && sharedPref.getBoolean("notifications_new_message_vibrate", true)) {
            mBuilder.setVibrate(new long[]{1000, 1000})
                    .setLights(Color.RED, 3000, 3000);
        }

        NotificationManager mNotifyManager = (NotificationManager)
                getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        mNotifyManager.notify(Strings.NOTIF_ID_HOUR, mBuilder.build());
    }

    private void notifyUser(Launch launch, int hours) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());

        String launchDate;
        String expandedText;
        String launchName = launch.getName();
        String launchPad = launch.getLocation().getName();

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        Intent mainActivityIntent = new Intent(this, MainActivity.class);
        mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent appIntent = PendingIntent.getActivity(this, 0, mainActivityIntent, 0);

        if (launch.getMissions().size() > 0) {
            expandedText = "Launch attempt in " + hours + " hours from " + launchPad + ". \n\n" + launch.getMissions().get(0).getDescription();
        } else {
            expandedText = "Launch attempt in " + hours + " hours from " + launchPad;
        }

        //Get launch date
        if (sharedPref.getBoolean("local_time", true)) {
            SimpleDateFormat df = new SimpleDateFormat("EEEE, MMM dd yyyy hh:mm a zzz");
            df.toLocalizedPattern();
            Date date = new Date(launch.getWindowstart());
            launchDate = df.format(date);
        } else {
            launchDate = launch.getWindowstart();
        }

        NotificationCompat.WearableExtender wearableExtender =
                new NotificationCompat.WearableExtender()
                        .setHintHideIcon(true)
                        .setBackground(BitmapFactory.decodeResource(this.getResources(),
                                R.drawable.nav_header));

        mBuilder.setContentTitle(launchName)
                .setContentText("Launch attempt in " + hours + " hours from " + launchPad)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(appIntent)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(expandedText)
                        .setSummaryText(launchDate))
                .extend(wearableExtender)
                .setSound(alarmSound)
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && sharedPref.getBoolean("notifications_new_message_vibrate", true)) {
            mBuilder.setPriority(Notification.PRIORITY_HIGH)
                    .setVibrate(new long[]{1000, 1000})
                    .setLights(Color.RED, 3000, 3000);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN && sharedPref.getBoolean("notifications_new_message_vibrate", true)) {
            mBuilder.setVibrate(new long[]{1000, 1000})
                    .setLights(Color.RED, 3000, 3000);
        }


        NotificationManager mNotifyManager = (NotificationManager)
                getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        mNotifyManager.notify(Strings.NOTIF_ID_DAY, mBuilder.build());
    }

    public static Calendar DateToCalendar(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal;
    }

    public void scheduleUpdate() {
        Timber.d("scheduleUpdate - Interval: %s", interval);
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        long nextUpdate = Calendar.getInstance().getTimeInMillis() + interval;
        Timber.d("scheduleUpdated at %s milli.", nextUpdate);

        if (BuildConfig.DEBUG) {
            storedLaunch = sharedPreference.getNextLaunch();
            upcomingLaunchList = sharedPreference.getNextLaunches();

            if (upcomingLaunchList != null && upcomingLaunchList.size() > 0) {
                nextLaunch = upcomingLaunchList.get(0);
            }

            if(nextLaunch != null && storedLaunch != null) {
                // Create a DateFormatter object for displaying date in specified format.
                SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss zz");
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(nextUpdate);

                String intevalString = String.format("%02d:%02d:%02d",
                        TimeUnit.MILLISECONDS.toHours(interval),
                        TimeUnit.MILLISECONDS.toMinutes(interval) -
                                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(interval)), // The change is in this line
                        TimeUnit.MILLISECONDS.toSeconds(interval) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(interval)));

                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
                mBuilder.setContentTitle("LaunchData Worked! - Next Launch")
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText("Next Launch = " + nextLaunch.getName() + "\n\nStored Launch = " + storedLaunch.getName())
                                .setSummaryText(String.format("Interval: %s | ", intevalString) + formatter.format(calendar.getTime())))
                        .setSmallIcon(R.drawable.ic_notification)
                        .setAutoCancel(true);

                NotificationManager mNotifyManager = (NotificationManager)
                        getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
                mNotifyManager.notify(Strings.NOTIF_ID + 1, mBuilder.build());
            }
        }

        sendToWear(sharedPreference.getNextLaunch());
        alarmManager.set(AlarmManager.RTC_WAKEUP, nextUpdate,
                PendingIntent.getBroadcast(this, 165432, new Intent(Strings.ACTION_CHECK_NEXT_LAUNCH_TIMER), 0));
    }

    // Create a data map and put data in it
    private void sendToWear(Launch launch) {
        if (launch != null && launch.getName() != null && launch.getNetstamp() != null) {
            Timber.v("Sending data...");
            PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/nextLaunch");

            putDataMapReq.getDataMap().putString(NAME_KEY, launch.getName());
            putDataMapReq.getDataMap().putInt(TIME_KEY, launch.getNetstamp());
            putDataMapReq.getDataMap().putLong("time", new Date().getTime());

            PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
            DataApi.DataItemResult dataItemResult = Wearable.DataApi
                    .putDataItem(mGoogleApiClient, putDataReq).await();
            Timber.v("Sent");
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Timber.d("onConnected");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Timber.e("onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Timber.e("onConnectionFailed %s",connectionResult.getErrorMessage());
    }

    @Override
    public void onDestroy(){
        Timber.d("onDestroy");
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            Timber.d("Google Client Disconnect");
            mGoogleApiClient.disconnect();
        }
    }
}