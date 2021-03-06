package me.calebjones.spacelaunchnow.content.interfaces;

import me.calebjones.spacelaunchnow.content.responses.base.LauncherResponse;
import me.calebjones.spacelaunchnow.content.responses.base.OrbiterResponse;
import me.calebjones.spacelaunchnow.content.responses.base.VehicleResponse;
import retrofit2.Call;
import retrofit2.http.GET;

public interface APIRequestInterface {

    @GET("orbiter")
    Call<OrbiterResponse> getOrbiter();

    @GET("launchers")
    Call<LauncherResponse> getLaunchers();

    @GET("vehicle")
    Call<VehicleResponse> getVehicles();
}
