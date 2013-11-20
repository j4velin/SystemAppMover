package de.j4velin.systemappmover;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

import android.os.Environment;

public class Logger {

	final static boolean LOG = false;
	
	private static FileWriter fw;
	private static Date date = new Date();
	private final static String APP = "SystemAppMover";

	public static void log(Throwable ex) {
		log(ex.getMessage());
		for (StackTraceElement ste : ex.getStackTrace()) {
			log(ste.toString());
		}
	}

	@SuppressWarnings("deprecation")
	public static void log(String msg) {
		if (!Logger.LOG)
			return;
		if (BuildConfig.DEBUG)
			android.util.Log.d(APP, msg);
		else {
			try {
				if (fw == null) {
					fw = new FileWriter(new File(Environment.getExternalStorageDirectory().toString() + "/" + APP + ".log"), true);
				}
				date.setTime(System.currentTimeMillis());
				fw.write(date.toLocaleString() + " - " + msg + "\n");
				fw.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	protected void finalize() throws Throwable {
		try {
			if (fw != null)
				fw.close();
		} finally {
			super.finalize();
		}
	}

}
