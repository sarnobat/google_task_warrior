package com.google.api.services.samples.calendar.cmdline;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

public class Delete {
	public static void main(String[] args) throws IOException {
		String itemToDelete = args[0];
		final String string = "/Users/sarnobat/.gcal_task_warrior";
		final File file = new File(string + "/tasks.json");
		String errands = FileUtils.readFileToString(file);
		JSONObject obj = new JSONObject(errands);
		System.out.println("Will delete "
				+ ((JSONObject) obj.get(itemToDelete)).getString("title"));
	}
}
