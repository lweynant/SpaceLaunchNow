package me.calebjones.spacelaunchnow.wear;

import android.support.v7.graphics.Target;

/**
 * Created by cjones on 11/17/16.
 */

public class Constants {
    public static final Target DARK;
    public static final Target LIGHT;
    public static final Target NEUTRAL;
    public static final Target DOMINANT;

    static {
        DOMINANT = new Target.Builder().setPopulationWeight(1f)
                .setSaturationWeight(0f)
                .setLightnessWeight(0f)
                .setExclusive(false)
                .build();

        DARK = new Target.Builder().setMinimumLightness(0f)
                .setTargetLightness(0.26f)
                .setMaximumLightness(0.5f)
                .setMinimumSaturation(0.1f)
                .setTargetSaturation(0.6f)
                .setMaximumSaturation(1f)
                .setPopulationWeight(0.18f)
                .setSaturationWeight(0.22f)
                .setLightnessWeight(0.60f)
                .setExclusive(false)
                .build();

        LIGHT = new Target.Builder().setMinimumLightness(0.50f)
                .setTargetLightness(0.74f)
                .setMaximumLightness(1.0f)
                .setMinimumSaturation(0.1f)
                .setTargetSaturation(0.7f)
                .setMaximumSaturation(1f)
                .setPopulationWeight(0.18f)
                .setSaturationWeight(0.22f)
                .setLightnessWeight(0.60f)
                .setExclusive(false)
                .build();

        NEUTRAL = new Target.Builder().setMinimumLightness(0.20f)
                .setTargetLightness(0.5f)
                .setMaximumLightness(0.8f)
                .setMinimumSaturation(0.1f)
                .setTargetSaturation(0.6f)
                .setMaximumSaturation(1f)
                .setPopulationWeight(0.18f)
                .setSaturationWeight(0.22f)
                .setLightnessWeight(0.60f)
                .setExclusive(false)
                .build();
    }
}
