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

import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialog;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.stericson.RootTools.RootTools;

import java.io.File;

/**
 * The main activity.
 * <p/>
 * All the logic starts in the AppPicker, which is started from the checkForRoot
 * method if root is available
 */
public class MoverActivity extends AppCompatActivity {

    public final static String SYSTEM_FOLDER_1 = "/system/priv-app/";
    public final static String SYSTEM_FOLDER_2 = "/system/app/";

    public final static String SYSTEM_DIR_TARGET =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ? SYSTEM_FOLDER_1 : SYSTEM_FOLDER_2;

    public static boolean SHOW_SYSTEM_APPS = false;

    /**
     * Shows an error dialog with the specified text
     *
     * @param text the error text
     */
    void showErrorDialog(final String text) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Error").setMessage(text)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, int id) {
                        try {
                            dialog.dismiss();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
        builder.create().show();
    }

    /**
     * Shows another warning when enabling the 'show system apps' option
     */
    void showSystemAppWarningDialog(final String text) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Warning").setMessage(text + " Did you make a backup?")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, int id) {
                        try {
                            dialog.dismiss();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            public void onClick(final DialogInterface dialog, int id) {
                try {
                    dialog.dismiss();
                    showErrorDialog("You should!");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        builder.create().show();
    }

    /**
     * Shows the initial warning dialog
     */
    void showWarningDialog() {
        final AppCompatDialog d = new AppCompatDialog(this);
        d.setTitle("Warning");
        d.setCancelable(false);
        d.setContentView(R.layout.warningdialog);

        final CheckBox c = (CheckBox) d.findViewById(R.id.c);
        final Button b = (Button) d.findViewById(R.id.b);

        c.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                b.setText(checked ? android.R.string.ok : android.R.string.cancel);
            }
        });

        b.setText(android.R.string.cancel);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (c.isChecked()) {
                    getSharedPreferences("settings", MODE_PRIVATE).edit()
                            .putBoolean("warningRead", true).commit();
                    d.dismiss();
                } else {
                    d.dismiss();
                    finish();
                }
            }
        });

        d.show();
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
        final ProgressDialog progress =
                ProgressDialog.show(this, "", "Waiting for root access", true);
        progress.show();
        final TextView error = (TextView) findViewById(R.id.error);
        final Handler h = new Handler();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean systemlessRoot = new File("/su").exists();
                if (!systemlessRoot && !RootTools.isRootAvailable()) {
                    if (progress == null || !progress.isShowing()) return;
                    progress.cancel();
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            error.setText(
                                    "Your device seems not to be rooted!\nThis app requires root access and does not work without.\n\nClick [here] to uninstall.");
                            // ask user to delete app on non-rooted devices
                            error.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    startActivity(new Intent(Intent.ACTION_DELETE,
                                            Uri.parse("package:de.j4velin.systemappmover")));
                                }
                            });
                        }
                    });
                    return;
                }
                final boolean root = systemlessRoot || RootTools.isAccessGiven();
                if (progress == null || !progress.isShowing()) return;
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

                               if (new File("/su/xbin/busybox").exists() ||
                                       RootTools.isBusyboxAvailable()) {
                                   CheckBox busyBox = (CheckBox) findViewById(R.id.busybox);
                                   busyBox.setChecked(true);
                                   busyBox.setText("BusyBox " + RootTools.getBusyBoxVersion());
                               } else {
                                   error.setText("No busybox found!\nClick here to download");
                                   error.setOnClickListener(new View.OnClickListener() {
                                       @Override
                                       public void onClick(View v) {
                                           RootTools.offerBusyBox(MoverActivity.this);
                                           finish();
                                       }
                                   });
                               }
                               if (root) {
                                   new AppPicker(MoverActivity.this).execute();
                                   if (!getSharedPreferences("settings", MODE_PRIVATE)
                                           .getBoolean("warningRead", false)) {
                                       showWarningDialog();
                                   }
                                   error.setText(
                                           "Use at your own risk! I won't take responsibility for damages on your device! Make a backup first!");
                                   final CheckBox showSystem =
                                           (CheckBox) findViewById(R.id.showsystem);
                                   showSystem.setOnCheckedChangeListener(
                                           new CompoundButton.OnCheckedChangeListener() {
                                               @Override
                                               public void onCheckedChanged(
                                                       final CompoundButton buttonView,
                                                       boolean isChecked) {
                                                   SHOW_SYSTEM_APPS = isChecked;
                                                   new AppPicker(MoverActivity.this).execute();
                                                   if (isChecked) {
                                                       String warning =
                                                               "Moving system apps is NOT recommended and will most definitely damage something on your system when doing so.";
                                                       if (Build.VERSION.SDK_INT >=
                                                               Build.VERSION_CODES.LOLLIPOP) {
                                                           warning +=
                                                                   " On Android 5.0+, this feature is highly experimental and most system apps won\'t ever work again once moved!";
                                                       }
                                                       showSystemAppWarningDialog(warning);
                                                   }


                                               }
                                           });
                               }

                           }
                       }

                );
            }
        }

        ).

                start();
    }

}
