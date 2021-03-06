package me.calebjones.spacelaunchnow.content.services;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.realm.Realm;
import jp.wasabeef.glide.transformations.BlurTransformation;
import jp.wasabeef.glide.transformations.gpu.BrightnessFilterTransformation;
import me.calebjones.spacelaunchnow.R;
import me.calebjones.spacelaunchnow.content.models.realm.LaunchRealm;
import me.calebjones.spacelaunchnow.content.models.realm.RocketDetailsRealm;
import me.calebjones.spacelaunchnow.utils.transformations.SaturationTransformation;
import timber.log.Timber;

import static me.calebjones.spacelaunchnow.content.models.Constants.BACKGROUND_KEY;
import static me.calebjones.spacelaunchnow.content.models.Constants.DATE_KEY;
import static me.calebjones.spacelaunchnow.content.models.Constants.DEFAULT_BLUR;
import static me.calebjones.spacelaunchnow.content.models.Constants.DEFAULT_DIM;
import static me.calebjones.spacelaunchnow.content.models.Constants.DEFAULT_GREY;
import static me.calebjones.spacelaunchnow.content.models.Constants.DEFAULT_RADIUS;
import static me.calebjones.spacelaunchnow.content.models.Constants.DYNAMIC_KEY;
import static me.calebjones.spacelaunchnow.content.models.Constants.NAME_KEY;
import static me.calebjones.spacelaunchnow.content.models.Constants.TIME_KEY;

public class UpdateWearService extends BaseService {

    public UpdateWearService() {
        super("UpdateWearService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        sendToWear(this);
    }

    // Create a data map and put data in it
    public static void sendToWear(Context context) {
        Realm realm = Realm.getDefaultInstance();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

        LaunchRealm launch = realm.where(LaunchRealm.class).greaterThan("net", new Date()).findAllSorted("net").first();
        if (launch != null && launch.getName() != null && launch.getNetstamp() != null) {
            Timber.v("Sending data to wear: %s", launch.getName());

            boolean dynamic = sharedPref.getBoolean("supporter_dynamic_background", false);
            boolean modify = sharedPref.getBoolean("wear_background_blur", true);
            if (dynamic) {
                if (launch.getRocket().getName() != null) {
                    if (launch.getRocket().getImageURL() != null && launch.getRocket().getImageURL().length() > 0 && !launch.getRocket().getImageURL().contains("placeholder")) {
                        Timber.v("Sending image %s", launch.getRocket().getImageURL());
                        sendImageToWear(context, launch.getRocket().getImageURL(), launch, modify);
                    } else {
                        String query;
                        if (launch.getRocket().getName().contains("Space Shuttle")) {
                            query = "Space Shuttle";
                        } else {
                            query = launch.getRocket().getName();
                        }

                        RocketDetailsRealm launchVehicle = realm.where(RocketDetailsRealm.class)
                                .contains("name", query)
                                .findFirst();
                        if (launchVehicle != null && launchVehicle.getImageURL() != null && launchVehicle.getImageURL().length() > 0) {
                            Timber.v("Sending image %s", launchVehicle.getImageURL());
                            sendImageToWear(context, launchVehicle.getImageURL(), launch, modify);
                            Timber.d("Glide Loading: %s %s", launchVehicle.getLV_Name(), launchVehicle.getImageURL());

                        } else {
                            sendImageToWear(context, context.getString(R.string.default_wear_image), launch, modify);
                        }
                    }
                } else {
                    sendImageToWear(context, context.getString(R.string.default_wear_image), launch, modify);
                }
            } else {
                sendImageToWear(context, context.getString(R.string.default_wear_image), launch, modify);
            }
        }
        realm.close();
    }

    public static void sendImageToWear(Context context, String image, final LaunchRealm launch, boolean modify) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .build();

        ConnectionResult connectionResult = mGoogleApiClient.blockingConnect(10, TimeUnit.SECONDS);
        if (connectionResult.isSuccess()) {
            Timber.v("Connected to Google API Client");


            int blur = sharedPreferences.getInt("BLUR_WEAR", DEFAULT_BLUR) + 1;
            int radius = sharedPreferences.getInt("RADIUS_WEAR", DEFAULT_RADIUS) + 1;
            int dim = sharedPreferences.getInt("DIM_WEAR", DEFAULT_DIM) + 1;
            int grey = sharedPreferences.getInt("GREY_WEAR", DEFAULT_GREY) + 1;
            final boolean dynamicText = sharedPreferences.getBoolean("wear_text_dynamic", false);

            final PutDataMapRequest putImageReq = PutDataMapRequest.create("/nextLaunch");

            /**
             * brightness value ranges from -1.0 to 1.0, with 0.0 as the normal level
             */
            float dimFloat = (float) (dim - 50) / 100;
            float satFloat = (float) grey / 100;
            Timber.v("Blur %s - Radius %s - Dim %sf - Saturation %sf", blur, radius, dimFloat, satFloat);

            if (modify) {
                try {
                    Bitmap resource = Glide.with(context)
                            .load(image)
                            .asBitmap()
                            .skipMemoryCache(true)
                            .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                            .transform(new SaturationTransformation(context, satFloat), new BlurTransformation(context, radius, blur), new BrightnessFilterTransformation(context, dimFloat))
                            .into(300, 300)
                            .get();

                    Asset asset = createAssetFromBitmap(resource);
                    putImageReq.getDataMap().putString(NAME_KEY, launch.getName());
                    putImageReq.getDataMap().putInt(TIME_KEY, launch.getNetstamp());
                    putImageReq.getDataMap().putLong(DATE_KEY, launch.getNet().getTime());
                    putImageReq.getDataMap().putLong("time", new Date().getTime());
                    putImageReq.getDataMap().putAsset(BACKGROUND_KEY, asset);
                    putImageReq.getDataMap().putBoolean(DYNAMIC_KEY, dynamicText);
                    PutDataRequest putDataReq = putImageReq.asPutDataRequest();
                    putImageReq.getDataMap().putLong("time", new Date().getTime());
                    Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
                    Timber.v("Data sent to wearable.");
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    Crashlytics.logException(e);
                }
            } else {
                try {
                    Bitmap resource = Glide.with(context)
                            .load(image)
                            .asBitmap()
                            .transform(new BrightnessFilterTransformation(context, -.1f))
                            .into(300, 300)
                            .get();
                    Asset asset = createAssetFromBitmap(resource);
                    putImageReq.getDataMap().putString(NAME_KEY, launch.getName());
                    putImageReq.getDataMap().putInt(TIME_KEY, launch.getNetstamp());
                    putImageReq.getDataMap().putLong(DATE_KEY, launch.getNet().getTime());
                    putImageReq.getDataMap().putLong("time", new Date().getTime());
                    putImageReq.getDataMap().putAsset(BACKGROUND_KEY, asset);
                    putImageReq.getDataMap().putBoolean(DYNAMIC_KEY, dynamicText);
                    PutDataRequest putDataReq = putImageReq.asPutDataRequest();
                    putImageReq.getDataMap().putLong("time", new Date().getTime());
                    Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
                    Timber.v("Data sent to wearable.");

                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    Crashlytics.logException(e);
                }
            }
        } else {
            Timber.v("Failed to connect to Google API Client");
        }
    }

    private static Asset createAssetFromBitmap(Bitmap bitmap) {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
    }
}
