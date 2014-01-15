package com.google.api.services.samples.calendar.cmdline;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.FetchProfile;
import javax.mail.Folder;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

public class ListUpdate {
	public static void main(String[] args) {
		final String string = "/Users/sarnobat/.gcal_task_warrior";
		final File file = new File(string + "/tasks.json");

		try {
			final JSONObject json = getErrands();
			new Thread() {
				@Override
				public void run() {
					if (file.exists()) {
						try {

							FileUtils.copyFile(file, new File(string
									+ "/tasks_last_displayed.json"));
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					new File(string).mkdir();
					LinkedList<String> lines = new LinkedList<String>();
					lines.add(json.toString());
					try {

						FileUtils.writeLines(file, lines);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}.start();
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (MessagingException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("List updated");
	}

	@SuppressWarnings("unused")
	private static JSONObject getErrands() throws NoSuchProviderException,
			MessagingException, IOException {
		Properties props = System.getProperties();
		String password = "varelA77";// System.getenv("GMAIL_PASSWORD");
		if (password == null) {
			throw new RuntimeException(
					"Please specify your password by running export GMAIL_PASSWORD=mypassword groovy mail.groovy");
		}
		props.setProperty("mail.store.protocol", "imap");
		Store theImapClient = Session.getInstance(props).getStore("imaps");
		theImapClient.connect("imap.gmail.com", "sarnobat.hotmail@gmail.com",
				password);
		Folder folder = theImapClient
				.getFolder("3 - Urg - time sensitive - this week");
		folder.open(Folder.READ_ONLY);

		Message[] msgs = folder.getMessages();

		FetchProfile fp = new FetchProfile();
		fp.add(FetchProfile.Item.ENVELOPE);
		fp.add("X-mailer");
		folder.fetch(msgs, fp);

		int i = 0;
		final JSONObject json = new JSONObject();
		for (Message aMessage : msgs) {
			Enumeration allHeaders = aMessage.getAllHeaders();
			String messageID = "<not found>";
			while (allHeaders.hasMoreElements()) {
				Header e = (Header) allHeaders.nextElement();
				if (e.getName().equals("Message-ID")) {
					messageID = e.getValue();
				}
			}

			i++;
			String title = aMessage.getSubject().split("@")[0].replace(
					"Reminder: ", "");
			MimeMultipart s = (MimeMultipart) aMessage.getContent();
			String body = (String) s.getBodyPart(0).getContent();
			if (body.trim().length() < 1) {
				System.out.println("body is empty");
			}
			Pattern pattern = Pattern.compile("eid=([^&" + '$' + "\\s]*)");

			if (!body.contains("eid")) {
				continue;
			}
			Matcher m = pattern.matcher(body);
			if (!m.find()) {
				throw new RuntimeException("eid not in string 2");
			}
			String eventID = m.group(1);
			JSONObject errandJsonObject = new JSONObject();
			errandJsonObject.put("eventID", eventID);
			errandJsonObject.put("title", title);
			errandJsonObject.put("Message-ID", messageID);
			json.put(Integer.toString(i), errandJsonObject);
		}
		return json;
	}
}
