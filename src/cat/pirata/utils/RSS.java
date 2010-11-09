package cat.pirata.utils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import org.apache.http.util.ByteArrayBuffer;

import android.database.Cursor;
import android.text.Html;
import android.util.Log;

public class RSS {

	private DbHelper db;

	// -PUBLIC-

	public RSS(DbHelper db) {
		this.db = db;
	}

	public Cursor getLastNews(int numlastnews) {
		return db.getLastNews(numlastnews);
	}

	public void refreshLastNews() {
		Cursor cr = db.getRssEnabled();
		do {
			Long lastAccess = cr.getLong(cr.getColumnIndex("lastAccess"));
			if (!isCached( System.currentTimeMillis(), lastAccess )) {
				String url = cr.getString(cr.getColumnIndex("url"));
				int id = cr.getInt(cr.getColumnIndex("id"));
				String rss = downloadRSS(url);
				if (rss != null) {
					Log.d("<DOWNLOAD>", url);
					db.updateFieldFromRSS(id, "lastAccess", System.currentTimeMillis());
					Long lastTime = db.getLastStr(id);
					parseRSS(id, rss, lastTime);
				}
			}
		} while (cr.moveToNext());
		cr.close();
	}

	public void clearOldNews() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date( System.currentTimeMillis() ));
		cal.roll(Calendar.MONTH, -1);
		db.clearOldNews( cal.getTimeInMillis() );
	}


	// -PRIVATE-

	private boolean isCached(Long timeNow, Long timeLast) {
		Calendar calNow = Calendar.getInstance();
		Calendar calLast = Calendar.getInstance();

		calNow.setTime(new Date( timeNow ));
		calLast.setTime(new Date( timeLast ));
		calLast.roll(Calendar.MINUTE, 10);

		return  (calNow.after(calLast)) ? false : true;
	}


	private void parseRSS(int id, String rss, Long lastTime) {
		int start, end = 0;
		int nextBlock = rss.indexOf("<item>", 0);
		String[] title = new String[] { "title", "link", "pubDate" };
		String[] value = new String[title.length];

		while (nextBlock != -1) {
			for (int i = 0; i < title.length; i++) {
				start = rss.indexOf(title[i], nextBlock) + title[i].length() + 1;
				end = rss.indexOf(title[i], start)-2;
				value[i] = rss.subSequence(start, end).toString();
			}
			Long pubDate = strDateToLong(value[2]);
			String StrpubDate = String.valueOf(pubDate).trim();
			String StrlastTime = String.valueOf(lastTime).trim();
			if (StrpubDate.compareTo(StrlastTime)==0) {
				return;
			}
			db.updateAllFieldsFromRow(id, pubDate, Html.fromHtml(value[0]).toString(), value[1]);
			nextBlock = rss.indexOf("<item>", nextBlock+1);
		}
	}

	public String downloadRSS(String url) {
		try {
			URL myURL = new URL(url);
			URLConnection ucon = myURL.openConnection();
			InputStream is = ucon.getInputStream();
			BufferedInputStream bis = new BufferedInputStream(is);
			ByteArrayBuffer baf = new ByteArrayBuffer(65535);
			int current = 0;
			while((current = bis.read()) != -1) {
				baf.append((byte)current);
			}
			return new String(baf.toByteArray());
		} catch (IOException e) {
			e.printStackTrace();
			return new String();
		}
	}

	private long strDateToLong(String lastBuildDateStr) {
		SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
		Date d;
		try {
			d = sdf.parse(lastBuildDateStr);
		} catch (ParseException e) {
			e.printStackTrace();
			return 0;
		}
		return d.getTime();
	}
}
