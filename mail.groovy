package com.google.api.services.samples.calendar.cmdline;

import java.io.IOException;
import java.util.Properties;

import javax.mail.FetchProfile;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMultipart;

public class Mail {
	public static void main(String[] args) {
		Properties props = System.getProperties();
		String password = System.getenv("GMAIL_PASSWORD");
		if (password == null){
			throw new RuntimeException("Please specify your password by running export GMAIL_PASSWORD=mypassword groovy mail.groovy");
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

			for (Message aMessage : msgs) {
				System.out.println(
						//aMessage.getMessageNumber()
						//+ "\t"
						"=== "
						+ aMessage.getSubject().split("@")[0].replace(
								"Reminder: ", "")
						+ " === "
								);

				 MimeMultipart s = (MimeMultipart) aMessage.getContent();
				 System.out.println(s.getBodyPart(0).getContent());
				 
				 System.out.println();

			}
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
