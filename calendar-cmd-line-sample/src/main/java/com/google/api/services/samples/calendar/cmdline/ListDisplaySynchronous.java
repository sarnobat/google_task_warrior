package com.google.api.services.samples.calendar.cmdline;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
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

import com.google.common.base.Preconditions;

public class ListDisplaySynchronous {
	static final String string = "/Users/sarnobat/.gcal_task_warrior";
	static final File file = new File(string + "/tasks.json");

	public static void main(String[] args) throws NoSuchProviderException,
			MessagingException, IOException {

		getErrands();

		System.out.println("List updated");
	}

	private static void getErrands() throws NoSuchProviderException,
			MessagingException, IOException {
		Message[] msgs = getMessages();
		System.out.println("Messages obtained");

		JSONObject json = createsJson(msgs);
		FileUtils.writeStringToFile(file, json.toString());
	}

	private static JSONObject createsJson(Message[] msgs)
			throws MessagingException {
		int i = 0;
		JSONObject json = new JSONObject();
		for (Message aMessage : msgs) {
			i++;
			// JSONObject messageMetadata =
			// checkNotNull(getMessageMetadata(aMessage));
			// json.put(Integer.toString(i),
			// messageMetadata);

			System.out.println(i + "\t" + aMessage.getSubject());
		}
		return json;
	}

	private static JSONObject getMessageMetadata(Message aMessage) {
		JSONObject errandJsonObject;
		try {
			errandJsonObject = new JSONObject();
			String title;

			title = aMessage.getSubject().split("@")[0].replace("Reminder: ",
					"");

			errandJsonObject.put("title", title);

			String eventID = getEventID(aMessage);
			errandJsonObject.put("eventID", eventID);

			String calendarName = getCalendarName(aMessage);
			errandJsonObject.put("calendar_name", calendarName);

			String messageID = getMessageID(aMessage);
			errandJsonObject.put("Message-ID", messageID);

			return errandJsonObject;
		} catch (MessagingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static String getCalendarName(Message aMessage) throws IOException,
			MessagingException {
		String calendarName;
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

		return calendarName;
	}

	private static String getEventID(Message aMessage) throws IOException,
			MessagingException {
		String eventID = "<none>";
		MimeMultipart s = (MimeMultipart) aMessage.getContent();
		String body = (String) s.getBodyPart(0).getContent();

		if (body.trim().length() < 1) {
			System.out.println("body is empty");
		}

		if (body.contains("eid")) {
			Pattern pattern = Pattern.compile("eid=([^&" + '$' + "\\s]*)");
			Matcher m = pattern.matcher(body);
			if (!m.find()) {
				throw new RuntimeException("eid not in string 2");
			}
			eventID = m.group(1);
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
		System.out.println("Connecting");
		Store theImapClient = connect();
		Folder folder = theImapClient
				.getFolder("3 - Urg - time sensitive - this week");
		System.out.println("Opening");
		folder.open(Folder.READ_ONLY);

		System.out.println("Getting Message list");
		Message[] msgs = folder.getMessages();

		FetchProfile fp = new FetchProfile();
		fp.add(FetchProfile.Item.ENVELOPE);
		System.out.print("Fetching attributes...");
		folder.fetch(msgs, fp);
		System.out.println("done");
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
