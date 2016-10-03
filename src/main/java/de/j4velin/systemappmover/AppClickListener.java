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

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

import com.stericson.RootTools.RootTools;

import java.io.File;
import java.util.List;

public class AppClickListener implements OnItemClickListener {

    private final AppPicker ap;

    public AppClickListener(final AppPicker a) {
        ap = a;
    }

    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                            long id) {
        if (position >= ap.apps.size()) return;

        if ("MOVED".equals(view.getTag())) {
            ap.activity.showErrorDialog("Please reboot before moving this app again");
            return;
        }

        ApplicationInfo tmp = ap.apps.get(position);
        boolean tmpAlreadySys = (tmp.flags & ApplicationInfo.FLAG_SYSTEM) == 1;

        // update necessary?
        if ((tmpAlreadySys && tmp.sourceDir.contains("/data/app/")) ||
                (!tmpAlreadySys && tmp.sourceDir.contains("/system/"))) {
            try {
                tmp = ap.pm.getApplicationInfo(tmp.packageName, 0);
            } catch (NameNotFoundException e1) {
                ap.activity.showErrorDialog("App not found");
                if (BuildConfig.DEBUG) Logger.log(e1);
                return;
            }
        }

        final ApplicationInfo app = tmp;
        final String appName = (String) app.loadLabel(ap.pm);
        final boolean alreadySys = (app.flags & ApplicationInfo.FLAG_SYSTEM) == 1;

        if (BuildConfig.DEBUG) Logger.log("Trying to move " + appName + " - " + app.packageName);

        if (app.packageName.equals(ap.activity.getPackageName())) {
            ap.activity.showErrorDialog("Can not move myself");
            if (BuildConfig.DEBUG) Logger.log("Can not move myself");
            return;
        }

        if (alreadySys && app.sourceDir.contains("/data/app/")) {
            if (BuildConfig.DEBUG) Logger.log("Need to remove updates first");
            AlertDialog.Builder builder = new AlertDialog.Builder(ap.activity);
            builder.setTitle("Error")
                    .setMessage("Can not move " + appName + ": Remove installed updates first.")
                    .setPositiveButton("Remove updates", new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, int id) {
                            try {
                                ap.activity.startActivity(new Intent(Intent.ACTION_DELETE,
                                        Uri.parse("package:" + app.packageName)));
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
        } else if (!alreadySys && tmp.sourceDir.contains("/system/")) {
            ap.activity.showErrorDialog("Can not move " + appName +
                    ": Undefined app status. You might need to reboot once.");
            if (BuildConfig.DEBUG) Logger.log(
                    "Undefined app status: IsSystem = " + alreadySys + " path = " + tmp.sourceDir);
            return;
        }

        String warning = null;

        if (!alreadySys && app.sourceDir.endsWith("pkg.apk")) {
            if (app.sourceDir.contains("asec")) {
                if (BuildConfig.DEBUG) Logger.log("Encrypted app? Path = " + app.sourceDir);
                warning = appName +
                        " seems to be an encrypted app and therefore might not be convertible to a system app! Continue at your own risk!";
            } else {
                if (BuildConfig.DEBUG) Logger.log("SD card? " + app.sourceDir);
                ap.activity.showErrorDialog(appName +
                        " is currently installed on SD card. Please move to internal memory before moving to /system/app/");
                return;
            }
        }

        AlertDialog.Builder b = new AlertDialog.Builder(ap.activity);
        b.setMessage("Convert " + appName + " to " + (alreadySys ? "normal" : "system") + " app?" +
                (warning != null ? "\n\nWarning: " + warning : ""));
        b.setPositiveButton(android.R.string.yes, new OnClickListener() {
                    @SuppressWarnings("deprecation")
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (RootTools.remount("/system", "rw")) {
                            try {
                                if (BuildConfig.DEBUG)
                                    Logger.log("process name: " + app.processName);

                                ActivityManager activityManager = (ActivityManager) ap.activity
                                        .getSystemService(Context.ACTIVITY_SERVICE);
                                List<RunningAppProcessInfo> runningProcInfo =
                                        activityManager.getRunningAppProcesses();
                                String[] pkgList;
                                for (RunningAppProcessInfo p : runningProcInfo) {
                                    pkgList = p.pkgList;
                                    for (String pkg : pkgList) {
                                        if (pkg.equals(app.processName)) {
                                            if (BuildConfig.DEBUG)
                                                Logger.log("killing: " + p.processName);
                                            RootTools.killProcess(p.processName);
                                            break;
                                        }
                                    }
                                }

                                if (BuildConfig.DEBUG) Logger.log("source: " + app.sourceDir);
                                if (!new File(app.sourceDir).exists()) {
                                    if (BuildConfig.DEBUG) Logger.log("source does not exist?!?");
                                    ap.activity.showErrorDialog("Can not access source file");
                                    return;
                                }

                                String fallbackFilename = appName.replaceAll("[^a-zA-Z0-9]+", "");

                                String newFile;
                                List<String> output;
                                if (!alreadySys) {
                                    if (app.sourceDir.endsWith("/pkg.apk") ||
                                            app.sourceDir.endsWith("/base.apk")) {
                                        newFile =
                                                MoverActivity.SYSTEM_DIR_TARGET + fallbackFilename +
                                                        ".apk";
                                    } else {
                                        newFile = app.sourceDir.replace("/data/app/",
                                                MoverActivity.SYSTEM_DIR_TARGET);
                                    }
                                } else {
                                    if (app.sourceDir.endsWith("/pkg.apk")) {
                                        newFile = "/data/app/" + app.packageName + ".apk";
                                    } else {
                                        if (app.sourceDir.contains(MoverActivity.SYSTEM_FOLDER_1)) {
                                            newFile = app.sourceDir
                                                    .replace(MoverActivity.SYSTEM_FOLDER_1,
                                                            "/data/app/");
                                        } else {
                                            newFile = app.sourceDir
                                                    .replace(MoverActivity.SYSTEM_FOLDER_2,
                                                            "/data/app/");
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                            String filename =
                                                    newFile.substring(newFile.lastIndexOf("/") + 1);
                                            if (BuildConfig.DEBUG)
                                                Logger.log("filename: " + filename);
                                            if (filename.equals("pkg.apk") ||
                                                    filename.equals("base.apk")) {
                                                filename = fallbackFilename;
                                            }
                                            newFile = "/data/app/" + filename;
                                        }
                                    }
                                }
                                String oldFile = app.sourceDir;
                                String cpcmd = "busybox cp " + oldFile + " " + newFile;
                                if (BuildConfig.DEBUG) Logger.log("command: " + cpcmd);

                                output = RootTools.sendShell(cpcmd, 10000);

                                if (output.size() > 1) {
                                    String error = "Error: ";
                                    for (String str : output) {
                                        if (str.length() > 1) {
                                            error += "\n" + str;
                                        }
                                    }
                                    if (BuildConfig.DEBUG) Logger.log(error);
                                    ap.activity.showErrorDialog(error);
                                } else {
                                    File f = new File(newFile);

                                    for (int i = 0; f.length() < 1 && i < 20; i++) {
                                        Thread.sleep(100);
                                    }

                                    if (BuildConfig.DEBUG) Logger.log(
                                            "file " + f.getAbsolutePath() + " size: " + f.length());

                                    if (f.length() > 1) {

                                        if (BuildConfig.DEBUG) Logger.log("changing chmod");
                                        output = RootTools
                                                .sendShell("busybox chmod 644 " + newFile, 5000);
                                        if (BuildConfig.DEBUG) {
                                            for (String str : output) {
                                                Logger.log(str);
                                            }
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                            if (BuildConfig.DEBUG)
                                                Logger.log("changing chown and chgrp");
                                            output = RootTools.sendShell(
                                                    new String[]{"busybox chown system " + newFile,
                                                            "busybox chgrp system " + newFile}, 500,
                                                    5000);
                                            if (BuildConfig.DEBUG) {
                                                for (String str : output) {
                                                    Logger.log(str);
                                                }
                                            }
                                        }

                                        view.setVisibility(View.GONE);
                                        view.setTag("MOVED");
                                        AlertDialog.Builder b2 =
                                                new AlertDialog.Builder(ap.activity);
                                        b2.setMessage(appName +
                                                " successfully moved, you need to reboot your device.\nReboot now?");
                                        if (BuildConfig.DEBUG) Logger.log("successfully moved");
                                        b2.setPositiveButton(android.R.string.yes,
                                                new OnClickListener() {
                                                    @Override
                                                    public void onClick(
                                                            final DialogInterface dialog,
                                                            int which) {
                                                        if (BuildConfig.DEBUG)
                                                            Logger.log("reboot now");
                                                        ap.activity.sendBroadcast(new Intent(
                                                                "de.j4velin.ACTION_SHUTDOWN"));
                                                        try {
                                                            dialog.dismiss();
                                                        } catch (Exception e) {
                                                        }
                                                        try {
                                                            RootTools.sendShell(
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
                                        b2.setNegativeButton(android.R.string.no,
                                                new OnClickListener() {
                                                    @Override
                                                    public void onClick(
                                                            final DialogInterface dialog,
                                                            int which) {
                                                        if (BuildConfig.DEBUG)
                                                            Logger.log("no reboot");
                                                        try {
                                                            dialog.dismiss();
                                                        } catch (Exception e) {
                                                        }
                                                    }
                                                });
                                        b2.create().show();
                                        String deletecmd = "busybox rm " + oldFile;
                                        if (BuildConfig.DEBUG) Logger.log("command: " + deletecmd);
                                        RootTools.sendShell(deletecmd, 10000);
                                    } else {
                                        ap.activity
                                                .showErrorDialog(appName + " could not be moved");
                                    }
                                }
                            } catch (Exception e) {
                                ap.activity.showErrorDialog(
                                        e.getClass().getName() + " " + e.getMessage());
                                e.printStackTrace();
                                if (BuildConfig.DEBUG) Logger.log(e);
                            } finally {
                                RootTools.remount("/system", "ro");
                                RootTools.remount("/mnt", "ro");
                            }
                        } else {
                            if (BuildConfig.DEBUG) Logger.log("can not remount target partition");
                            ap.activity.showErrorDialog("Could not remount /system");
                        }
                    }
                }

        );
        b.setNegativeButton(android.R.string.no, new

                OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            dialog.dismiss();
                        } catch (Exception e) {
                        }
                    }
                }

        );
        b.create().

                show();
    }
}
