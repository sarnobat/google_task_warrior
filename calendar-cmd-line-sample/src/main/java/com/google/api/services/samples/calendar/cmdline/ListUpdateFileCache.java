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

@Deprecated
// This approach is problematic because of weak consistency
public class ListUpdateFileCache {
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
		Message[] msgs = getMessages();

		int i = 0;
		final JSONObject json = new JSONObject();
		for (Message aMessage : msgs) {
			i++;
			JSONObject errandJsonObject = getMessageMetadata(aMessage);
			json.put(Integer.toString(i), errandJsonObject);

		}
		return json;
	}

	private static JSONObject getMessageMetadata(Message aMessage)
			throws MessagingException, IOException {
		JSONObject errandJsonObject;
		{
			errandJsonObject = new JSONObject();
			String title = getTitle(aMessage);
			errandJsonObject.put("title", title);

			String eventID = getEventID(aMessage);
			errandJsonObject.put("eventID", eventID);

			String calendarName = getCalendarName(aMessage);
			errandJsonObject.put("calendar_name", calendarName);

			String messageID = getMessageID(aMessage);
			errandJsonObject.put("Message-ID", messageID);
		}
		return errandJsonObject;
	}

	private static String getTitle(Message aMessage) throws MessagingException {
		String title = aMessage.getSubject().split("@")[0].replace(
				"Reminder: ", "");
		return title;
	}

	private static String getCalendarName(Message aMessage) throws IOException,
			MessagingException {
		String calendarName;
		{
			MimeMultipart s = (MimeMultipart) aMessage.getContent();
			String body1 = (String) s.getBodyPart(0).getContent();
			if (body1.contains("Calendar:")) {
				Pattern pattern = Pattern.compile("Calendar: (.*)");
				Matcher m = pattern.matcher(body1);
				if (!m.find()) {
					throw new RuntimeException("eid not in string 2");
				}
				calendarName = m.group(1);
			} else {
				calendarName = "<not found>";
			}
		}
		return calendarName;
	}

	private static String getEventID(Message aMessage) throws IOException,
			MessagingException {
		String eventID = "<none>";
		{
			MimeMultipart s = (MimeMultipart) aMessage.getContent();
			{
				String body = (String) s.getBodyPart(0).getContent();

				if (body.trim().length() < 1) {
					System.out.println("body is empty");
				}

				if (body.contains("eid")) {
					Pattern pattern = Pattern.compile("eid=([^&" + '$'
							+ "\\s]*)");
					Matcher m = pattern.matcher(body);
					if (!m.find()) {
						throw new RuntimeException("eid not in string 2");
					}
					eventID = m.group(1);
				}
			}
		}
		return eventID;
	}

	private static String getMessageID(Message aMessage)
			throws MessagingException {
		Enumeration allHeaders = aMessage.getAllHeaders();
		String messageID = "<not found>";
		while (allHeaders.hasMoreElements()) {
			Header e = (Header) allHeaders.nextElement();
			if (e.getName().equals("Message-ID")) {
				messageID = e.getValue();
			}
		}
		return messageID;
	}

	private static Message[] getMessages() throws NoSuchProviderException,
			MessagingException {
		Store theImapClient = connect();
		Folder folder = theImapClient
				.getFolder("3 - Urg - time sensitive - this week");
		folder.open(Folder.READ_ONLY);

		Message[] msgs = folder.getMessages();

		FetchProfile fp = new FetchProfile();
		fp.add(FetchProfile.Item.ENVELOPE);
		fp.add("X-mailer");
		folder.fetch(msgs, fp);
		return msgs;
	}

	private static Store connect() throws NoSuchProviderException,
			MessagingException {
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
		return theImapClient;
	}
}
