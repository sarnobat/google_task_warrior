package com.google.api.services.samples.calendar.cmdline;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;

import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

public class Delete {
	static final String string = "/Users/sarnobat/.gcal_task_warrior";
	static final String string2 = string + "/tasks_last_displayed.json";
	static final File file = new File(string2);

	public static void main(String[] args) throws IOException,
			NoSuchProviderException, MessagingException {
		String itemToDelete = args[0];
		String errands = FileUtils.readFileToString(file);
		JSONObject obj = new JSONObject(errands);
		String messageIdToDelete = ((JSONObject) obj.get(itemToDelete))
				.getString("Message-ID");
		System.out.println("Will delete [" + messageIdToDelete + "] "
				+ ((JSONObject) obj.get(itemToDelete)).getString("title"));
		Message[] messages = getMessages();
		for (Message aMessage : messages) {
			String aMessageID = getMessageID(aMessage);
			if (aMessageID.equals(messageIdToDelete)) {
				aMessage.setFlag(Flags.Flag.DELETED, true);
				System.out.println("Deleted " + aMessage.getSubject());
				break;
			}
		}

		deleteMessageFromLocalJson(itemToDelete, messageIdToDelete);
		System.out.println("Files updated");
	}

	private static void deleteMessageFromLocalJson(String itemToDelete,
			String messageIdToDelete) throws IOException {
		String displayedFileContents = FileUtils.readFileToString(file);
		JSONObject displayedFileJson = new JSONObject(displayedFileContents);
		JSONObject removed = (JSONObject) displayedFileJson
				.remove(itemToDelete);
		if (!messageIdToDelete.equals(removed.getString("Message-ID"))) {
			throw new RuntimeException(removed.getString("title"));
		}
		FileUtils.writeStringToFile(file, displayedFileJson.toString());
		File file2 = new File(string
				+ "/tasks.json");
		String latestFileContents = FileUtils.readFileToString(file2);
		JSONObject latestFileJson = new JSONObject(latestFileContents);
		JSONObject removed2 = (JSONObject) latestFileJson.remove(itemToDelete);
		if (!messageIdToDelete.equals(removed2.getString("Message-ID"))) {
			throw new RuntimeException(removed2.getString("title"));
		}
		FileUtils.writeStringToFile(file2, latestFileJson.toString());

	}

	private static Message[] getMessages() throws NoSuchProviderException,
			MessagingException {
		Store theImapClient = connect();
		Folder folder = theImapClient
				.getFolder("3 - Urg - time sensitive - this week");
		folder.open(Folder.READ_WRITE);

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

}
