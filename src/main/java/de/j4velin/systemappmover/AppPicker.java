/*
 * Copyright 2014 Thomas Hoffmann
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.j4velin.systemappmover;

import java.util.ArrayList;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.ProgressDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.widget.ListView;


/**
 * Class to list all installed app.
 * 
 * The actual moving happens in the AppClickListener class when an item is clicked.
 * 
 */
public class AppPicker extends AsyncTask<Void, Void, Void> {

	List<Drawable> icons;
	List<ApplicationInfo> apps;
	PackageManager pm;
	private ProgressDialog progress;
	final MoverActivity activity;

	public AppPicker(final MoverActivity a) {
		activity = a;
	}

	@Override
	protected void onPreExecute() {
		pm = activity.getPackageManager();
		progress = ProgressDialog.show(activity, "", "Loading apps", true);
	}

	@Override
	protected void onPostExecute(Void a) {
		try {
			progress.cancel();
		} catch (IllegalArgumentException e) {
			if (BuildConfig.DEBUG)
				Logger.log(e);
		}
		if (apps == null || apps.isEmpty()) {
			activity.showErrorDialog("Error loadings apps!");
		} else {
			ListView liste = (ListView) activity.findViewById(R.id.apps);
			liste.setAdapter(new EfficientAdapter(activity, this));
			liste.setOnItemClickListener(new AppClickListener(this));
		}
	}

	@Override
	protected Void doInBackground(Void... params) {
		// load all apps and their icons, sort them alphabetical
		apps = pm.getInstalledApplications(0);
		try {
			Collections.sort(apps, new Comparator<ApplicationInfo>() {
				public int compare(final ApplicationInfo app1, final ApplicationInfo app2) {
					final CharSequence label1 = app1.loadLabel(pm);
					final CharSequence label2 = app2.loadLabel(pm);
					if (label1 == null || label1.length() == 0 || label2 == null || label2.length() == 0) {
						return 0;
					} else {
						try {
							int pos = 0;
							while (label1.length() > pos + 1 && label2.length() > pos + 1
									&& label1.charAt(pos) == label2.charAt(pos)) {
								pos++;
							}
							return (label1.charAt(pos) - label2.charAt(pos));
						} catch (NullPointerException npe) {
							return 0;
						}
					}
				}
			});
		} catch (IllegalArgumentException iae) {
		}

		icons = new ArrayList<Drawable>(apps.size());
		try {
			for (int i = 0; i < apps.size(); i++) {
				icons.add(apps.get(i).loadIcon(pm));
			}
		} catch (OutOfMemoryError oom) {
		}
		return null;
	}

}