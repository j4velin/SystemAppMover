/*
 * Copyright 2012 Thomas Hoffmann
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

import com.stericson.RootTools.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * The main activity.
 * 
 * Quick & dirty solution - all the logic is in the onItemClickListener
 * 
 */
public class MoverActivity extends Activity {

	private final static String SYSTEM_APP_FOLDER = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT ? "/system/priv-app/"
			: "/system/app/";

	/**
	 * Shows an error dialog with the specified text
	 * 
	 * @param text
	 *            the error text
	 */
	private void showErrorDialog(final String text) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Error").setMessage(text).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(final DialogInterface dialog, int id) {
				try {
					dialog.dismiss();
				} catch (Exception e) {
				}
			}
		});
		builder.create().show();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		RootTools.debugMode = false;
		checkForRoot();
	}

	/**
	 * Uses the RootTools library to check for root and busybox
	 */
	private void checkForRoot() {
		final ProgressDialog progress = ProgressDialog.show(this, "", "Waiting for root access", true);
		progress.show();
		final TextView error = (TextView) findViewById(R.id.error);
		final Handler h = new Handler();
		new Thread(new Runnable() {
			@Override
			public void run() {
				if (!RootTools.isRootAvailable()) {
					if (progress == null || !progress.isShowing())
						return;
					progress.cancel();
					h.post(new Runnable() {
						@Override
						public void run() {
							error.setText("Your device seems not to be rooted!\nThis app requires root access and does not work without.\n\nClick [here] to uninstall.");
							// ask user to delete app on non-rooted devices
							error.setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View v) {
									startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:de.j4velin.systemappmover")));
								}
							});
						}
					});
					return;
				}
				final boolean root = RootTools.isAccessGiven();
				if (progress == null || !progress.isShowing())
					return;
				progress.cancel();
				h.post(new Runnable() {
					@Override
					public void run() {
						if (root) {
							((CheckBox) findViewById(R.id.root)).setChecked(true);
						} else {
							error.setText("No root access granted - click here to recheck");
							error.setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View v) {
									checkForRoot();
								}
							});
							return;
						}

						if (RootTools.isBusyboxAvailable()) {
							CheckBox busyBox = (CheckBox) findViewById(R.id.busybox);
							busyBox.setChecked(true);
							busyBox.setText("BusyBox " + RootTools.getBusyBoxVersion());
							if (root)
								new AppPicker().execute();
						} else {
							error.setText("No busybox found!\nClick here to download");
							error.setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View v) {
									RootTools.offerBusyBox(MoverActivity.this);
								}
							});

							return;
						}
						error.setText("Use at your own risk! I won't take responsibility for damages on your device!");
					}
				});
			}
		}).start();
	}

	private class AppPicker extends AsyncTask<Void, Void, Void> {

		private List<Drawable> icons;
		private List<ApplicationInfo> apps;
		private PackageManager pm;
		private ProgressDialog progress;

		@Override
		protected void onPreExecute() {
			pm = getPackageManager();
			progress = ProgressDialog.show(MoverActivity.this, "", "Loading apps", true);
		}

		@Override
		protected void onPostExecute(Void a) {
			try {
				progress.cancel();
			} catch (IllegalArgumentException e) {
				if (Logger.LOG)
					Logger.log(e);
			}
			if (apps == null || apps.isEmpty()) {
				showErrorDialog("Error loadings apps!");
			} else {
				ListView liste = (ListView) findViewById(R.id.apps);
				liste.setAdapter(new EfficientAdapter(MoverActivity.this));

				// maybe create a separate class instead of anonymous?
				liste.setOnItemClickListener(new OnItemClickListener() {
					public void onItemClick(AdapterView<?> parent, final View view, final int position, long id) {
						if (position >= apps.size())
							return;

						if ("MOVED".equals(view.getTag())) {
							showErrorDialog("Please reboot before moving this app again");
							return;
						}

						ApplicationInfo tmp = apps.get(position);
						boolean tmpAlreadySys = (tmp.flags & ApplicationInfo.FLAG_SYSTEM) == 1;

						// update necessary?
						if ((tmpAlreadySys && tmp.sourceDir.contains("/data/app/"))
								|| (!tmpAlreadySys && tmp.sourceDir.contains(SYSTEM_APP_FOLDER))) {
							try {
								tmp = pm.getApplicationInfo(tmp.packageName, 0);
							} catch (NameNotFoundException e1) {
								showErrorDialog("App not found");
								if (Logger.LOG)
									Logger.log(e1);
								return;
							}
						}

						final ApplicationInfo app = tmp;
						final String appName = (String) app.loadLabel(pm);
						final boolean alreadySys = (app.flags & ApplicationInfo.FLAG_SYSTEM) == 1;

						if (Logger.LOG)
							Logger.log("Trying to move " + appName + " - " + app.packageName);

						if (app.packageName.equals(getPackageName())) {
							showErrorDialog("Can not move myself");
							if (Logger.LOG)
								Logger.log("Can not move myself");
							return;
						}

						if (alreadySys && app.sourceDir.contains("/data/app/")) {
							if (Logger.LOG)
								Logger.log("Need to remove updates first");
							AlertDialog.Builder builder = new AlertDialog.Builder(MoverActivity.this);
							builder.setTitle("Error").setMessage("Can not move " + appName + ": Remove installed updates first.")
									.setPositiveButton("Remove updates", new DialogInterface.OnClickListener() {
										public void onClick(final DialogInterface dialog, int id) {
											try {
												startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:"
														+ app.packageName)));
												dialog.dismiss();
											} catch (Exception e) {
											}
										}
									}).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
										public void onClick(final DialogInterface dialog, int id) {
											try {
												dialog.dismiss();
											} catch (Exception e) {
											}
										}
									});
							builder.create().show();
							return;
						} else if (!alreadySys && tmp.sourceDir.contains(SYSTEM_APP_FOLDER)) {
							showErrorDialog("Can not move " + appName + ": Undefined app status. You might need to reboot once.");
							if (Logger.LOG)
								Logger.log("Undefined app status: IsSystem = " + alreadySys + " path = " + tmp.sourceDir);
							return;
						}

						if (!alreadySys && app.sourceDir.endsWith("pkg.apk")) {
							if (app.sourceDir.contains("asec")) {
								if (Logger.LOG)
									Logger.log("Paid app? Path = " + app.sourceDir);
								showErrorDialog(appName
										+ " seems to be a paid app and therefore can not be converted to system app due to limitations by the Android system");
							} else {
								if (Logger.LOG)
									Logger.log("SD card? " + app.sourceDir);
								showErrorDialog(appName
										+ " is currently installed on SD card. Please move to internal memory before moving to /system/app/");
							}
							return;
						}

						AlertDialog.Builder b = new AlertDialog.Builder(MoverActivity.this);
						b.setMessage("Convert " + appName + " to " + (alreadySys ? "normal" : "system") + " app?");
						b.setPositiveButton(android.R.string.yes, new OnClickListener() {
							@SuppressWarnings("deprecation")
							@Override
							public void onClick(DialogInterface dialog, int which) {
								if (RootTools.remount("/system", "rw")) {
									try {

										if (Logger.LOG)
											Logger.log("process name: " + app.processName);

										ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
										List<RunningAppProcessInfo> runningProcInfo = activityManager.getRunningAppProcesses();
										String[] pkgList;
										for (RunningAppProcessInfo p : runningProcInfo) {
											pkgList = p.pkgList;
											for (String pkg : pkgList) {
												if (pkg.equals(app.processName)) {
													if (Logger.LOG)
														Logger.log("killing: " + p.processName);
													RootTools.killProcess(p.processName);
													break;
												}
											}
										}

										if (Logger.LOG)
											Logger.log("source: " + app.sourceDir);

										String mvcmd, newFile;
										List<String> output = null;
										if (!alreadySys) {
											if (app.sourceDir.endsWith("/pkg.apk")) {
												newFile = SYSTEM_APP_FOLDER + app.packageName + "-asec.apk";
												if (!RootTools.remount("/mnt", "rw")) {
													if (Logger.LOG)
														Logger.log("Can not remount /mnt");
													showErrorDialog("Can not remount /mnt/asec");
													return;
												}
												mvcmd = "busybox mv " + app.sourceDir + " " + newFile;
												if (Logger.LOG)
													Logger.log("source ends with /pkg.apk -> paid app");
											} else {
												newFile = app.sourceDir.replace("/data/app/", SYSTEM_APP_FOLDER);
												mvcmd = "busybox mv " + app.sourceDir + " " + SYSTEM_APP_FOLDER;
											}
										} else {
											if (app.sourceDir.endsWith("/pkg.apk")) {
												newFile = "/data/app/" + app.packageName + ".apk";
												mvcmd = "busybox mv " + app.sourceDir + " " + newFile;
											} else {
												newFile = app.sourceDir.replace(SYSTEM_APP_FOLDER, "/data/app/");
												mvcmd = "busybox mv " + app.sourceDir + " /data/app/";
											}
										}
										if (Logger.LOG)
											Logger.log("command: " + mvcmd);

										output = RootTools.sendShell(mvcmd, 10000);

										if (output.size() > 1) {
											String error = "Error: ";
											for (String str : output) {
												if (str.length() > 1) {
													error += "\n" + str;
												}
											}
											if (Logger.LOG)
												Logger.log(error);
											showErrorDialog(error);
										} else {

											File f = new File(newFile);

											for (int i = 0; f.length() < 1 && i < 20; i++) {
												Thread.sleep(100);
											}

											if (Logger.LOG)
												Logger.log("file " + f.getAbsolutePath() + " size: " + f.length());

											if (f.length() > 1) {

												if (!alreadySys) {
													output = RootTools.sendShell("busybox chmod 644 " + newFile, 5000);
													if (Logger.LOG) {
														for (String str : output) {
															Logger.log(str);
														}
													}
												}

												view.setVisibility(View.GONE);
												view.setTag("MOVED");
												AlertDialog.Builder b2 = new AlertDialog.Builder(MoverActivity.this);
												b2.setMessage(appName
														+ " successfully moved, you need to reboot your device.\nReboot now?");
												if (Logger.LOG)
													Logger.log("successfully moved");
												b2.setPositiveButton(android.R.string.yes, new OnClickListener() {
													@Override
													public void onClick(final DialogInterface dialog, int which) {
														if (Logger.LOG)
															Logger.log("reboot now");
														sendBroadcast(new Intent("de.j4velin.ACTION_SHUTDOWN"));
														try {
															RootTools
																	.sendShell(
																			"am broadcast -a android.intent.action.ACTION_SHUTDOWN",
																			5000);
															try {
																Thread.sleep(1000);
															} catch (InterruptedException e) {
															}
															RootTools.sendShell("reboot", 5000);
														} catch (Exception e) {
														}
													}
												});
												b2.setNegativeButton(android.R.string.no, new OnClickListener() {
													@Override
													public void onClick(final DialogInterface dialog, int which) {
														if (Logger.LOG)
															Logger.log("no reboot");
														try {
															dialog.dismiss();
														} catch (Exception e) {
														}
													}
												});
												b2.create().show();
											} else {
												showErrorDialog(appName + " could not be moved");
											}
										}
									} catch (Exception e) {
										showErrorDialog(e.getClass().getName() + " " + e.getMessage());
										e.printStackTrace();
										if (Logger.LOG)
											Logger.log(e);
									}
									RootTools.remount("/system", "ro");
									RootTools.remount("/mnt", "ro");
								} else {
									if (Logger.LOG)
										Logger.log("can not remount /system");
									showErrorDialog("Could not remount /system");
								}
							}
						});
						b.setNegativeButton(android.R.string.no, new OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								try {
									dialog.dismiss();
								} catch (Exception e) {
								}
							}
						});
						b.create().show();
					}
				});
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

		private class EfficientAdapter extends BaseAdapter {
			private LayoutInflater mInflater;
			private Handler handler = new Handler();

			public EfficientAdapter(final Context context) {
				mInflater = LayoutInflater.from(context);
			}

			public int getCount() {
				return apps.size();
			}

			public Object getItem(int position) {
				return position;
			}

			public long getItemId(int position) {
				return position;
			}

			public View getView(final int position, View convertView, ViewGroup parent) {
				if (position < apps.size()) {
					final ViewHolder holder;

					if (convertView == null || !(convertView.getTag() instanceof ViewHolder)) {
						convertView = mInflater.inflate(R.layout.listviewitem, null);
						holder = new ViewHolder();
						holder.text = (TextView) convertView.findViewById(R.id.text);
						holder.pack = (TextView) convertView.findViewById(R.id.pack);
						holder.icon = (ImageView) convertView.findViewById(R.id.icon);
						holder.system = (TextView) convertView.findViewById(R.id.system);
						convertView.setTag(holder);
					} else {
						holder = (ViewHolder) convertView.getTag();
					}

					handler.post(new Runnable() {
						public void run() {
							holder.text.setText(apps.get(position).loadLabel(pm));
							holder.pack.setText(apps.get(position).packageName);
							holder.system.setVisibility(((apps.get(position).flags & ApplicationInfo.FLAG_SYSTEM) == 1) ? View.VISIBLE
									: View.GONE);
							// sollte eig immer der fall sein, trotzdem
							// ArrayIndexOutofBounds crashreport erhalten
							if (position < icons.size())
								holder.icon.setImageDrawable(icons.get(position));
						}
					});

				}
				return convertView;
			}

			private class ViewHolder {
				TextView text;
				TextView pack;
				ImageView icon;
				TextView system;
			}

		}

	}

}