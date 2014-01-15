package com.google.api.services.samples.calendar.cmdline;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.RegEx;
import javax.mail.FetchProfile;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

@Deprecated // Too much faulty behavior from weak consistency
public class ListDisplayFromFileCache {
	public static void main(String[] args) throws IOException {
		final String string = "/Users/sarnobat/.gcal_task_warrior";
		final File file = new File(string + "/tasks.json");
		if (file.exists()) {
			try {
				String errandsStr = FileUtils.readFileToString(file);
				JSONObject errandsJson = new JSONObject(errandsStr);
				SortedMap<Integer, String> errandMap = new TreeMap<Integer, String>();
				for (Object errandNumber : errandsJson.keySet()) {
					String key = (String) errandNumber;
					JSONObject val = (JSONObject) errandsJson.get(key);
					String title = val.getString("title");
					errandMap.put(Integer.parseInt(key), title);
				}
				for (Object key : errandMap.keySet()) {
					String title = errandMap.get(key);
					System.out.println(key + "\t" + title);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
