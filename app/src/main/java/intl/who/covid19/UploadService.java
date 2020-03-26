/*-
 * Copyright (c) 2020 Sygic a.s.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package intl.who.covid19;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

public class UploadService extends JobService {

	private static final int JOB_ID_ENCOUNTERS = 1;
	private static final int JOB_ID_LOCATIONS = 2;

	public static void start(Context context) {
		App.log("UploadService: start");
		long sendingPeriodMinutes = App.get(context).getRemoteConfig().getLong(App.RC_BATCH_SEDNING_FREQUENCY);
		long periodMillis = (BuildConfig.DEBUG ? 1 : sendingPeriodMinutes) * 60_000;
		scheduleJob(context, JOB_ID_ENCOUNTERS, periodMillis);
		scheduleJob(context, JOB_ID_LOCATIONS, periodMillis);
	}

	private static void scheduleJob(Context context, int jobId, long periodMillis) {
		JobScheduler scheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
		for (JobInfo info : scheduler.getAllPendingJobs()) {
			if (info.getId() == jobId && info.getIntervalMillis() == periodMillis) {
				App.log("UploadService: job ("+jobId+") already scheduled");
				return;
			}
		}

		JobInfo.Builder builder = new JobInfo.Builder(jobId, new ComponentName(context, UploadService.class));
		builder.setPersisted(true);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			builder.setPeriodic(periodMillis, periodMillis / 6);
		} else {
			builder.setPeriodic(periodMillis);
		}
		int result = scheduler.schedule(builder.build());
		if (result <= 0) {
			throw new RuntimeException("Can't schedule upload job ("+jobId+"), result = " + result);
		}
	}

	private final BeaconConnection beaconConnection = new BeaconConnection();
	private JobParameters currentEncJob;
	private boolean binding;

	@Override
	public boolean onStartJob(JobParameters params) {
		App.log("UploadService: onStartJob, id = " + params.getJobId());
		if (params.getJobId() == JOB_ID_ENCOUNTERS) {
			// TODO: this isn't quite right, we don't wait for this request to finish
			App.get(this).updateCountryCode(null);
		}
		switch (params.getJobId()) {
			case JOB_ID_ENCOUNTERS:
				currentEncJob = params;
				if (!bindService(new Intent(this, BeaconService.class), beaconConnection, 0)) {
					App.log("UploadService: Can't bind to BeaconService");
					return false;
				}
				binding = true;
				return true;

			case JOB_ID_LOCATIONS:
				App.get(this).getLocationQueue().send(() -> {
					App.log("UploadService: on locations sent");
					jobFinished(params, false);
				});
				return true;

		}
		return false;
	}

	@Override
	public boolean onStopJob(JobParameters params) {
		App.log("UploadService: onStopJob");
		switch (params.getJobId()) {
			case JOB_ID_ENCOUNTERS:
				if (binding) {
					binding = false;
					try {
						unbindService(beaconConnection);
					} catch (RuntimeException ex) {
						// service already unbound
					}
				}
				currentEncJob = null;
				break;
		}
		return false;
	}

	private class BeaconConnection implements ServiceConnection {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			App.log("UploadService: onServiceConnected");
			BeaconService.Binder beacon = (BeaconService.Binder) service;
			beacon.cutLiveEncounters();

			JobParameters localParams = currentEncJob;
			if (localParams != null) {
				App.get(UploadService.this).getEncounterQueue().send(() -> {
					App.log("UploadService: on encounters sent");
					jobFinished(localParams, false);
				});
			}

			binding = false;
			unbindService(beaconConnection);
		}
		@Override
		public void onServiceDisconnected(ComponentName name) {
			App.log("UploadService: onServiceDisconnected");
			binding = false;
		}
	}

}
