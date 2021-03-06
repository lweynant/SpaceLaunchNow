package me.calebjones.spacelaunchnow.content.jobs;

import android.support.annotation.NonNull;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobRequest;

import me.calebjones.spacelaunchnow.content.models.Constants;
import me.calebjones.spacelaunchnow.content.services.LaunchDataService;


public class NextLaunchJob extends Job {

    public static final String TAG = Constants.ACTION_CHECK_NEXT_LAUNCH_TIMER;

    @NonNull
    @Override
    protected Result onRunJob(Params params) {
        if(LaunchDataService.getNextLaunches(getContext())){
            return Result.SUCCESS;
        } else {
            return Result.RESCHEDULE;
        }
    }

    @Override
    protected void onReschedule(int newJobId) {
        // the rescheduled job has a new ID
    }

    public static void scheduleJob(long interval) {
//        if (interval < 300000){
//            interval = 300000;
//        }

        JobRequest.Builder builder = new JobRequest.Builder(NextLaunchJob.TAG)
                .setExact(interval)
                .setUpdateCurrent(true);

        builder.build().schedule();
    }
}
