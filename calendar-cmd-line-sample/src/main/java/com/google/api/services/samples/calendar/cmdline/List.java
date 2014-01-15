package com.google.api.services.samples.calendar.cmdline;

import java.io.IOException;
import java.util.Properties;
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

import org.json.JSONObject;

public class List {
	public static void main(String[] args) {
		Properties props = System.getProperties();
		String password = "varelA77";// System.getenv("GMAIL_PASSWORD");
		if (password == null) {
			throw new RuntimeException(
					"Please specify your password by running export GMAIL_PASSWORD=mypassword groovy mail.groovy");
		}
		props.setProperty("mail.store.protocol", "imap");
		try {
			Store theImapClient = Session.getInstance(props).getStore("imaps");
			theImapClient.connect("imap.gmail.com",
					"sarnobat.hotmail@gmail.com", password);
			System.out.println(theImapClient);
			Folder folder = theImapClient
					.getFolder("3 - Urg - time sensitive - this week");
			System.out.println(folder.getURLName());
			folder.open(Folder.READ_ONLY);

			Message[] msgs = folder.getMessages();

			FetchProfile fp = new FetchProfile();
			fp.add(FetchProfile.Item.ENVELOPE);
			fp.add("X-mailer");
			folder.fetch(msgs, fp);

			int i = 0;
			JSONObject json = new JSONObject();
			for (Message aMessage : msgs) {
				i++;
				String title = aMessage.getSubject().split("@")[0].replace(
						"Reminder: ", "");
				System.out.println(i + "\t" + title);
				MimeMultipart s = (MimeMultipart) aMessage.getContent();
				String body = (String) s.getBodyPart(0).getContent();
				if (body.trim().length() < 1) {
					System.out.println("body is empty");
				}
				Pattern pattern = Pattern.compile("eid=([^&" +'$' +"\\s]*)");

				if (!body.contains("eid")) {
					continue;
				}
				Matcher m = pattern.matcher(body);
				if (!m.find()) {
					throw new RuntimeException("eid not in string 2");
				}
				String eventID = m.group(1);
				System.out.println(eventID);
				JSONObject errandJsonObject = new JSONObject();
				errandJsonObject.put("eventID", eventID);
				errandJsonObject.put("title", title);
				json.put(Integer.toString(i), errandJsonObject);
			}

			System.out.println(json.toString());
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (MessagingException e) {
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
